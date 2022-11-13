package zio.metrics.connectors.insight

import java.util.UUID

import zio._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.insight.ClientMessage.{InsightMetricState, MetricKeyWithId}

trait InsightPublisher {

  /**
   * Return all metric keys.
   */
  def getAllKeys(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics]

  /**
   * Return metrics for the provided selection of metric keys.
   */
  def getMetrics(selection: Iterable[UUID])(implicit trace: Trace): UIO[ClientMessage.MetricsResponse]

  /**
   * Store metric key and state pairs.
   */
  def update(event: MetricEvent): ZIO[Any, Nothing, Unit]
}

private[insight] case class InsightPublisherImpl(current: Ref[Map[UUID, InsightMetricState]]) extends InsightPublisher {

  /**
   * Return all metric keys.
   */
  def getAllKeys(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics] =
    for {
      values    <- current.get.map(_.values)
      keysWithId = values.map(value => MetricKeyWithId(value.id, value.key))
      res       <- ZIO.succeed(ClientMessage.AvailableMetrics(keysWithId.toSet))
    } yield res

  /**
   * Return metrics for the provided selection of metric keys.
   */
  def getMetrics(selection: Iterable[UUID])(implicit trace: Trace): UIO[ClientMessage.MetricsResponse] =
    ZIO.collectPar(selection)(s => current.get.map(_.get(s))).map { states =>
      ClientMessage.MetricsResponse(states.collect { case Some(state) => state }.toSet)
    }

  def update(event: MetricEvent): ZIO[Any, Nothing, Unit] = event match {
    case MetricEvent.Unchanged(_, _, _) => ZIO.unit
    case _                              =>
      InsightEncoder.encode(event).flatMap { case (uuid, state) =>
        current.update(_.updated(uuid, state))
      }
  }
}

private[insight] object InsightPublisher {
  def make: ZIO[Any, Nothing, InsightPublisher] = for {
    current <- Ref.make(Map.empty[UUID, InsightMetricState])
  } yield InsightPublisherImpl(current)

}
