package zio.metrics.connectors

import scala.util.control.NonFatal

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object prometheus {

  /** Provides a new `PrometheusPublisher` with empty text. */
  def publisherLayer: ULayer[PrometheusPublisher] = ZLayer.fromZIO(PrometheusPublisher.make)

  /**
   * Starts the poll loop. On each poll it renders every metric in Prometheus text format and replaces the publisher's
   * text with the result. A metric that fails to encode is left out of that poll's text. Requires a `MetricsConfig` and a
   * `PrometheusPublisher`, and provides nothing.
   *
   * {{{
   * import java.time.Duration
   *
   * import zio._
   *
   * import zio.metrics.connectors._
   * import zio.metrics.connectors.prometheus._
   *
   * val prometheus: ZLayer[Any, Nothing, PrometheusPublisher] =
   *   ZLayer.make[PrometheusPublisher](
   *     ZLayer.succeed(MetricsConfig(Duration.ofSeconds(10))),
   *     publisherLayer,
   *     prometheusLayer,
   *   )
   * }}}
   */
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
