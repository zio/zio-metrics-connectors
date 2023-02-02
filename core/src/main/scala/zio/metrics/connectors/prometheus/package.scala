package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  lazy val publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  lazy val prometheusLayer: ZLayer[MetricsConfig & PrometheusPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      ZIO.service[PrometheusPublisher].flatMap(clt => MetricsClient.make(prometheusHandler(clt))).unit,
    )

  private def prometheusHandler(clt: PrometheusPublisher): Iterable[MetricEvent] => UIO[Unit] = events =>
    for {
      old            <- clt.get
      reportComplete <- ZIO.foreach(Chunk.fromIterable(events)) { e =>
                          PrometheusEncoder.encode(e).catchAll { t =>
                            ZIO.succeed(Chunk.empty)
                          }
                        }
      _              <- clt.set(reportComplete.flatten.addString(new StringBuilder(old.length), "\n").toString())
    } yield ()

}
