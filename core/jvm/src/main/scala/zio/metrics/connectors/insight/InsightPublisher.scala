package zio.metrics.connectors.insight

import zio._
import zio.metrics.MetricKey
import zio.stm.TSet

class InsightPublisher private (current: TSet[MetricKey[Any]]) {
  def get(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics] =
    for {
      c <- current.get
      res <- ZIO.succeed(ClientMessage.AvailableMetrics(c))
    } yield res

  def set(next: Set[MetricKey[Any]])(implicit trace: Trace): UIO[Unit] =
    current.put(next)
}

object InsightPublisher {
  def make = for {
    current <- TSet.empty[MetricKey[Any]]
  } yield new InsightPublisher(current)
}
