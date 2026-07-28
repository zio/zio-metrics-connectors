package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {

  @deprecated("Use the statsdUDP or statsdUDS from the zio.metrics.connectors.statsd package instead", "2.4.0")
  def statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      for {
        config       <- ZIO.service[StatsdConfig]
        statsdClient <- StatsdClient.make
        _            <- metricsClient(statsdClient, encoder(config))
      } yield ()
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over UDP network protocol.
   */
  def statsdUDP: URLayer[StatsdConfig & MetricsConfig, StatsdClient] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      for {
        config       <- ZIO.service[StatsdConfig]
        statsdClient <- StatsdClient.make
        _            <- metricsClient(statsdClient, encoder(config))
      } yield statsdClient
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over unix domain socket (UDS).
   */
  def statsdUDS: URLayer[DatagramSocketConfig & MetricsConfig, StatsdClient] =
    ZLayer.scoped[DatagramSocketConfig & MetricsConfig] {
      for {
        config       <- ZIO.service[DatagramSocketConfig]
        statsdClient <- DatagramSocketClient.make
        _            <- metricsClient(statsdClient, encoder(config))
      } yield statsdClient
    }

  private def encoder(config: StatsdConfig): StatsdEncoder =
    StatsdEncoder(constantTags = config.constantTags, suffix = config.suffix, prefix = config.prefix)

  private def encoder(config: DatagramSocketConfig): StatsdEncoder =
    StatsdEncoder(constantTags = config.constantTags, suffix = config.suffix, prefix = config.prefix)

  private def metricsClient(client: StatsdClient, encoder: StatsdEncoder): URIO[MetricsConfig & Scope, Unit] =
    MetricsClient.make { events =>
      val evtFilter: MetricEvent => Boolean = {
        case _: MetricEvent.Unchanged => false
        case _                        => true
      }

      ZIO
        .foreachDiscard(events.collect { case e if evtFilter(e) => encoder.pureEncode(e) }) { encoded =>
          ZIO
            .attempt(client.send(encoded))
            .ignore // TODO: Do we want to at least log a problem sending the metrics ?
        }
    }
}
