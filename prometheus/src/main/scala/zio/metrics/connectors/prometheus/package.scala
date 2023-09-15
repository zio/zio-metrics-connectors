package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  lazy val publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  lazy val prometheusLayer: ZLayer[MetricsConfig & PrometheusPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      for {
        pub <- ZIO.service[PrometheusPublisher]
        _   <- MetricsClient.make(prometheusHandler(pub))
      } yield (),
    )

  private def prometheusHandler(clt: PrometheusPublisher): Iterable[MetricEvent] => UIO[Unit] =
    events =>
      for {
        old            <- clt.get
        reportComplete <- ZIO.foreach(Chunk.fromIterable(events)) { e =>
                            PrometheusEncoder.encode(e).catchAll { _ =>
                              ZIO.succeed(Chunk.empty)
                            }
                          }
        groupedReport  <- ZIO.succeed(groupMetricByType(reportComplete))
        _              <- clt.set(groupedReport.flatten.addString(new StringBuilder(old.length), "\n").toString())
      } yield ()

  def groupMetricByType(report: Chunk[Chunk[String]]): Chunk[Chunk[String]] =
    Chunk.fromIterable(report.groupBy(thm => thm.take(2)).map { case (th, thmChunk) =>
      th ++ thmChunk.map(_.drop(2)).flatten
    })
}
