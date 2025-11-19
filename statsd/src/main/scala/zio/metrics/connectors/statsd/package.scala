package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {

  @deprecated("Use the statsdUDP or statsdUDS from the zio.metrics.connectors.statsd package instead", "2.4.0")
  lazy val statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      StatsdClient.make.flatMap(metricsClient)
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over UDP network protocol.
   */
  lazy val statsdUDP: URLayer[StatsdConfig & MetricsConfig, Unit] =
    ZLayer.scoped[StatsdConfig & MetricsConfig] {
      StatsdClient.make.flatMap(metricsClient)
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over unix domain socket (UDS).
   */
  lazy val statsdUDS: URLayer[DatagramSocketConfig & MetricsConfig, Unit] =
    ZLayer.scoped[DatagramSocketConfig & MetricsConfig] {
      DatagramSocketClient.make.flatMap(metricsClient)
    }

  private def metricsClient(client: StatsdClient): URIO[MetricsConfig & Scope, Unit] =
    MetricsClient.make { events =>
      val evtFilter: MetricEvent => Boolean = {
        case MetricEvent.Unchanged(_, _, _) => false
        case _                              => true
      }

      val send = ZIO
        .foreachDiscard(events.filter(evtFilter))(evt =>
          for {
            encoded <- StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
            _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(client.send(encoded)))
          } yield (),
        )

      // TODO: Do we want to at least log a problem sending the metrics ?
      send.catchAll(_ => ZIO.unit)
    }
}
