package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object statsd {

  @deprecated("Use the statsdUDP or statsdUDS from the zio.metrics.connectors.statsd package instead", "2.4.0")
  lazy val statsdLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      StatsdClient.make.flatMap(clt => MetricsClient.runScoped(statsdHandler(clt))).unit,
    )

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over UDP network protocol.
   */
  lazy val statsdUDP: ZLayer[StatsdConfig & MetricsConfig, Nothing, StatsdClient] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[StatsdConfig]
        client <- StatsdClient.make.provideSome[Scope](ZLayer.succeed(config))
      } yield client
    }

  /**
   * Creates a layer that provides a StatsdClient that sends metrics over unix domain socket (UDS).
   */
  lazy val statsdUDS: ZLayer[DatagramSocketConfig & MetricsConfig, Nothing, StatsdClient] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[DatagramSocketConfig]
        client <- DatagramSocketClient.make.provideSome[Scope](ZLayer.succeed(config))
      } yield client
    }

  private[connectors] def statsdHandler(clt: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter: MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _                              => true
    }

    val send = ZIO
      .foreachDiscard(events.filter(evtFilter))(evt =>
        for {
          encoded <- StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(clt.send(encoded)))
        } yield (),
      )

    // TODO: Do we want to at least log a problem sending the metrics ?
    send.catchAll(_ => ZIO.unit)
  }
}
