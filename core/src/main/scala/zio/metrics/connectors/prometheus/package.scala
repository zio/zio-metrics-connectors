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
                            PrometheusEncoder.encode(e, descriptionKey = Some(config.descriptionKey)).catchAll { _ =>
                              ZIO.succeed(Chunk.empty)
                            }
                          }
        groupedReport <- ZIO.succeed(groupMetricByType(reportComplete))
        _              <- clt.set(groupedReport.flatten.addString(new StringBuilder(old.length), "\n").toString())
      } yield ()

  def groupMetricByType(report: Chunk[Chunk[String]]): Chunk[Chunk[String]] = {
    Chunk.fromIterable(report.groupMap(thm => thm.take(2))(thm => thm.drop(2)).map {
      case (th, m) => th.appendedAll(m.flatten)
    })
  }
}
