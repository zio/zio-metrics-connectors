package zio.metrics.connectors

import zio._
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.StatsdClient
import zio.metrics.connectors.statsd.StatsdConfig

package object datadog {

  lazy val datadogLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit]        =
    ZLayer.scoped(
      for {
        clt      <- StatsdClient.make
        _        <- MetricsClient.make(statsdHandler(clt))
        queue    <- Queue.bounded[(MetricKey[MetricKeyType.Histogram], Double)](100) // TODO make this configurable
        listener <- DataDogListener.make(clt, queue)
        _         = MetricsClient.registerListener(MetricKeyType.Histogram, listener)
      } yield (),
    )
  private def statsdHandler(clt: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter: MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _                              => true
    }

    val send = ZIO
      .foreach(events.filter(evtFilter))(evt =>
        for {
          encoded <- statsd.StatsdEncoder.encode(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(clt.send(encoded)))
        } yield (),
      )
      .unit

    // TODO: Do we want to at least log a problem sending the metrics ?
    send.catchAll(_ => ZIO.unit)
  }
}
