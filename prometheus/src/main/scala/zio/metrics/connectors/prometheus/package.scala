package zio.metrics.connectors

import scala.util.control.NonFatal

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  def publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  def prometheusLayer: ZLayer[MetricsConfig & PrometheusPublisher, Nothing, Unit] =
    ZLayer.scoped {
      for {
        pub <- ZIO.service[PrometheusPublisher]
        _   <- MetricsClient.make(prometheusHandler(pub))
      } yield ()
    }

  private def prometheusHandler(clt: PrometheusPublisher): Iterable[MetricEvent] => UIO[Unit] =
    events =>
      clt.get.flatMap { old =>
        val reportComplete =
          events.map { e =>
            try PrometheusEncoder.unsafeEncode(e)
            catch {
              case e if NonFatal(e) => Chunk.empty
            }
          }
        val groupedReport  = groupMetricByType(reportComplete)
        val newReport      = groupedReport.flatten.addString(new StringBuilder(old.length), "\n").toString()
        clt.set(newReport)
      }

  private[connectors] def groupMetricByType(report: Iterable[Chunk[String]]): Iterable[Chunk[String]] =
    report
      .groupBy(_.take(2))
      .map { case (th, thmChunk) => th ++ thmChunk.flatMap(_.drop(2)) }

}
