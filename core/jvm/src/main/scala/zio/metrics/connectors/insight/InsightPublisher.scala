package zio.metrics.connectors.insight

import java.util.UUID

import zio._
import zio.metrics.connectors.insight.ClientMessage.{InsightMetricState, MetricKeyWithId}

private[connectors] class InsightPublisher private (current: Ref[Map[UUID, InsightMetricState]]) {

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
    for {
      filtered <- current.get.map(_.filter { case (key: UUID, _) =>
                    selection.exists(key == _)
                  })
      result    = ClientMessage.MetricsResponse(filtered.values.toSet)
    } yield result

  /**
   * Store metric key and state pairs.
   */
  def set(next: (UUID, InsightMetricState))(implicit trace: Trace): UIO[Unit] =
    current.update(_ + next)
}

private[connectors] object InsightPublisher {
  def make: ZIO[Any, Nothing, InsightPublisher] = for {
    current <- Ref.make(Map.empty[UUID, InsightMetricState])
  } yield new InsightPublisher(current)
}
