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
        reportComplete <- ZIO.foreach(events)(evt =>
                            for {
                              reportEvent <-
                                PrometheusEncoder
                                  .encode(evt, descriptionKey = Some(config.descriptionKey))
                                  .map(_.mkString("\n"))
                                  .catchAll(_ => ZIO.succeed(""))
                            } yield reportEvent,
                          )
        _              <- clt.set(reportComplete.mkString("\n"))
      } yield ()

}
