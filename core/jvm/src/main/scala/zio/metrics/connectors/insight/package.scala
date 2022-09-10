package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object insight {

  lazy val publisherLayer: ULayer[InsightPublisher] = ZLayer.fromZIO(InsightPublisher.make)

  lazy val insightLayer: ZLayer[MetricsConfig & InsightPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      ZIO.service[InsightPublisher].flatMap(clt => MetricsClient.make(insightHandler(clt))).unit,
    )

  private def insightHandler(clt: InsightPublisher): Iterable[MetricEvent] => UIO[Unit] = events =>
    for {
      reportedMetrics <- ZIO.foreach(events)(evt =>
                           for {
                             metric <- InsightEncoder.encode(evt)
                           } yield metric,
                         )
      _               <- clt.set(reportedMetrics)
    } yield ()

}
