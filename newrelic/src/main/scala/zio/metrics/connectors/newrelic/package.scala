package zio.metrics.connectors

import zio._
import zio.http.Client
import zio.metrics.connectors.internal.MetricsClient

package object newrelic {

  lazy val newRelicLayer: ZLayer[MetricsConfig & NewRelicConfig, Nothing, Unit] =
    ZLayer.makeSome[MetricsConfig & NewRelicConfig, Unit](
      make,
      Client.default.orDie,
    )

  private lazy val make: URLayer[MetricsConfig & NewRelicConfig & Client, Unit] =
    ZLayer.scoped {
      for {
        encoder <- newRelicEncoder
        client  <- NewRelicClient.make
        handler  = newRelicHandler(encoder, client)
        _       <- MetricsClient.make(handler)
      } yield ()
    }

  private lazy val newRelicEncoder =
    Clock.instant.map(NewRelicEncoder.apply)

  private def newRelicHandler(
    encoder: NewRelicEncoder,
    client: NewRelicClient,
  ): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter: MetricEvent => Boolean = !_.isInstanceOf[MetricEvent.Unchanged]

    ZIO.foreachDiscard(events.filter(evtFilter))(evt =>
      for {
        encoded <- encoder.encode(evt).orElseSucceed(Chunk.empty)
        _       <- ZIO.whenDiscard(encoded.nonEmpty)(client.send(encoded))
      } yield (),
    )

  }

}
