package zio.metrics.connectors.insight

import zio._
import zio.metrics.MetricKey
import zio.metrics.MetricState
import zio.stm.TMap

class InsightPublisher private (current: TMap[MetricKey[Any], MetricState[Any]]) {
  def getAllKeys(implicit trace: Trace): UIO[ClientMessage.AvailableMetrics] =
    for {
      keys <- current.keys.commit
      res  <- ZIO.succeed(ClientMessage.AvailableMetrics(keys.toSet))
    } yield res

  def set(next: (MetricKey[Any], MetricState[Any]))(implicit trace: Trace): UIO[Unit] =
    for {
      _ <- ZIO.succeed(println(next))
      _ <- current.put(next._1, next._2).commit
    } yield ()
}

object InsightPublisher {
  def make = (for {
    current <- TMap.empty[MetricKey[Any], MetricState[Any]]
  } yield new InsightPublisher(current)).commit
}
