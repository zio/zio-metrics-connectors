package zio.metrics.connectors.insight

import zio._
import zio.metrics.MetricKey
import zio.stm.TSet

class InsightPublisher private (current: TSet[MetricKey[Any]]) {
  def getKeys(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics] =
    for {
      c   <- current.toSet.commit
      res <- ZIO.succeed(ClientMessage.AvailableMetrics(c))
    } yield res

  def set(next: Iterable[MetricKey[Any]])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(println(next)) *> current.put(next.head).commit // FIXME: add complete iterable to set
}

object InsightPublisher {
  def make = (for {
    current <- TSet.empty[MetricKey[Any]]
  } yield new InsightPublisher(current)).commit
}
