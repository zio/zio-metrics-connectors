package zio.metrics.connectors

import zio._
import zio.http.Client
import zio.metrics.connectors.internal.MetricsClient

package object newrelic {

  /**
   * Starts New Relic publishing. It encodes each new or changed metric, queues the JSON, and posts it in batches over HTTP
   * with the default zio-http `Client`. Requires a `MetricsConfig` and a `NewRelicConfig`.
   *
   * {{{
   * import java.time.Duration
   *
   * import zio._
   *
   * import zio.metrics.connectors._
   * import zio.metrics.connectors.newrelic._
   *
   * val newRelic: ZLayer[Any, Nothing, Unit] =
   *   ZLayer.make[Unit](
   *     ZLayer.succeed(MetricsConfig(Duration.ofSeconds(10))),
   *     NewRelicConfig.fromEnvLayer,
   *     newRelicLayer,
   *   )
   * }}}
   */
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
