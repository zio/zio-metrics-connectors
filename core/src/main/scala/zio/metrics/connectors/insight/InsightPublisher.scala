package zio.metrics.connectors.insight

import java.util.UUID

import zio._
import zio.metrics.connectors.insight.ClientMessage.{InsightMetricState, MetricKeyWithId}

sealed abstract case class InsightPublisher private (private val current: Ref[Map[UUID, InsightMetricState]]) {

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

  /**
   * Store metric key and state pairs.
   */
  private[insight] def set(next: (UUID, InsightMetricState))(implicit trace: Trace): UIO[Unit] =
    current.update(_ + next)
}

private[insight] object InsightPublisher {
  private[insight] def make: ZIO[Any, Nothing, InsightPublisher] = for {
    current <- Ref.make(Map.empty[UUID, InsightMetricState])
  } yield new InsightPublisher(current) {}
}
