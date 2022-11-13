package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object insight {

  private lazy val publisherLayer: ULayer[InsightPublisher] = ZLayer.fromZIO(InsightPublisher.make)

  lazy val insightLayer: ZLayer[MetricsConfig, Nothing, InsightPublisher] =
    (publisherLayer ++ ZLayer.service[MetricsConfig]) >+> ZLayer.fromZIO(
      ZIO.service[InsightPublisher].flatMap(clt => MetricsClient.make(insightHandler(clt))).unit,
    )

  private def insightHandler(clt: InsightPublisher): Iterable[MetricEvent] => UIO[Unit] =
    events =>
      ZIO
        .foreach(events)(clt.update)
        .unit

}
