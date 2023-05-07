package zio.metrics.connectors

import zio._
import zio.internal.RingBuffer
import zio.metrics.{MetricClient, MetricKey, MetricKeyType}
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.{StatsdClient, StatsdConfig}

package object datadog {

  lazy val datadogLayer: ZLayer[DatadogConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[DatadogConfig]
        clt     <- StatsdClient.make.provideSome[Scope](ZLayer.succeed(StatsdConfig(config.host, config.port)))
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener = new DataDogListener(queue)
        _       <- Unsafe.unsafe(implicit unsafe =>
                     ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)))(_ =>
                       ZIO.succeed(MetricClient.removeListener(listener)),
                     ),
                   )
        _       <- DataDogEventProcessor.make(clt, queue)
        _       <- MetricsClient.make(datadogHandler(clt, config))
      } yield (),
    )

  private[connectors] def datadogHandler(
    client: StatsdClient,
    config: DatadogConfig,
  ): Iterable[MetricEvent] => UIO[Unit] = events => {
    val evtFilter: MetricEvent => Boolean = if (config.sendUnchanged) { _ => true }
    else {
      case MetricEvent.Unchanged(_, _, _) => false
      case _                              => true
    }

    val encoder = DatadogEncoder.encoder(config)

    val send = ZIO
      .foreachDiscard(events.filter(evtFilter))(evt =>
        for {
          encoded <- encoder(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(client.send(encoded)))
        } yield (),
      )

    send.ignore
  }

}
