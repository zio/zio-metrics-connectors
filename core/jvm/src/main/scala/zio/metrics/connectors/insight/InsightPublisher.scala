package zio.metrics.connectors.insight

import zio._
import zio.metrics.MetricKey
import zio.metrics.MetricState

class InsightPublisher private (current: Ref[Map[MetricKey[Any], MetricState[Any]]]) {

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
  def getMetrics(selection: Iterable[MetricKey[Any]])(implicit trace: Trace): UIO[ClientMessage.MetricsResponse] = ???

  /**
   * Store metric key and state pairs.
   */
  def set(next: (MetricKey[Any], MetricState[Any]))(implicit trace: Trace): UIO[Unit] =
    for {
      _ <- ZIO.succeed(println(next))
      _ <- current.update(_ + next)
    } yield ()
}

object InsightPublisher {
  def make = for {
    current <- Ref.make(Map.empty[MetricKey[Any], MetricState[Any]])
  } yield new InsightPublisher(current)
}
