package zio.metrics.connectors.insight

import java.time.Instant

import zio._
import zio.metrics.{MetricKey, MetricState}

private[connectors] class InsightPublisher private (current: Ref[Map[MetricKey[Any], MetricState[Any]]]) {

  /**
   * Return all metric keys.
   */
  def getAllKeys(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics] =
    for {
      keys <- current.get.map(_.keys)
      res  <- ZIO.succeed(ClientMessage.AvailableMetrics(keys.toSet))
    } yield res

  /**
   * Return metrics for the provided selection of metric keys.
   */
  def getMetrics(selection: Iterable[MetricKey[Any]])(implicit trace: Trace): UIO[ClientMessage.MetricsResponse] =
    for {
      filtered <- current.get.map(_.filter { case (key: MetricKey[Any], _) =>
                    selection.exists(key == _)
                  })
      result    = ClientMessage.MetricsResponse(
                    Instant.now,
                    filtered
                      .toSet,
                  )
    } yield result

  /**
   * Store metric key and state pairs.
   */
  def set(next: (MetricKey[Any], MetricState[Any]))(implicit trace: Trace): UIO[Unit] =
    current.update(_ + next)
}

private[connectors] object InsightPublisher {
  def make: ZIO[Any, Nothing, InsightPublisher] = for {
    current <- Ref.make(Map.empty[MetricKey[Any], MetricState[Any]])
  } yield new InsightPublisher(current)
}
