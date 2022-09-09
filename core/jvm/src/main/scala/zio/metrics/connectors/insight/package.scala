package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object insight {

  lazy val publisherLayer: ULayer[InsightPublisher] = ZLayer.fromZIO(InsightPublisher.make)

  lazy val insightLayer: ZLayer[MetricsConfig & InsightPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      ZIO.serviceWithZIO[InsightPublisher](MetricsClient.make(insightHandler(_))).unit,
    )

  private def insightHandler(clt: InsightPublisher): Iterable[MetricEvent] => UIO[Unit] = events =>
    for {
      _ <- ZIO.foreach(events)(evt =>
             for {
               reportEvent <- InsightEncoder.encode(evt)
               _           <- clt.set(reportEvent)
             } yield (),
           )
    } yield ()

}
