package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {

  @deprecated("Use the statsdUDP or statsdUDS from the zio.metrics.connectors.statsd package instead", "2.4.0")
  def statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      StatsdClient.make.flatMap(metricsClient)
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over UDP network protocol.
   */
  def statsdUDP: URLayer[StatsdConfig & MetricsConfig, StatsdClient] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      StatsdClient.make.flatMap { statsdClient =>
        for {
          _ <- metricsClient(statsdClient)
        } yield statsdClient
      }
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over unix domain socket (UDS).
   */
  def statsdUDS: URLayer[DatagramSocketConfig & MetricsConfig, StatsdClient] =
    ZLayer.scoped[DatagramSocketConfig & MetricsConfig] {
      DatagramSocketClient.make.flatMap { statsdClient =>
        for {
          _ <- metricsClient(statsdClient)
        } yield statsdClient
      }
    }

  private def metricsClient(client: StatsdClient): URIO[MetricsConfig & Scope, Unit] =
    MetricsClient.make { events =>
      val evtFilter: MetricEvent => Boolean = {
        case _: MetricEvent.Unchanged => false
        case _                        => true
      }

      ZIO
        .foreachDiscard(events.collect { case e if evtFilter(e) => StatsdEncoder.pureEncode(e) }) { encoded =>
          ZIO
            .attempt(client.send(encoded))
            .ignore // TODO: Do we want to at least log a problem sending the metrics ?
        }
    }
}
