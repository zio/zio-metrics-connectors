package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  lazy val publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  lazy val prometheusLayer: ZLayer[MetricsConfig & PrometheusPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      for {
        conf <- ZIO.service[MetricsConfig]
        pub  <- ZIO.service[PrometheusPublisher]
        _    <- MetricsClient.make(prometheusHandler(pub, conf))
      } yield (),
    )

  private def prometheusHandler(clt: PrometheusPublisher, config: MetricsConfig): Iterable[MetricEvent] => UIO[Unit] =
    events =>
      for {
        old            <- clt.get
        reportComplete <- ZIO.foreach(Chunk.fromIterable(events)) { e =>
                            PrometheusEncoder.encode(e, descriptionKey = Some(config.descriptionKey)).catchAll { t =>
                              ZIO.succeed(Chunk.empty)
                            }
                          }
        _              <- clt.set(reportComplete.flatten.addString(new StringBuilder(old.length), "\n").toString())
      } yield ()

}
