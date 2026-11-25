package zio.metrics.connectors

import zio._
import zio.internal.RingBuffer
import zio.metrics.{MetricClient, MetricKey, MetricKeyType}
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.{StatsdClient, StatsdConfig}

package object datadog {

  /**
   * Starts the Datadog pipeline with a `StatsdClient` for `config.host` and `config.port`, and the settings of
   * `DatadogConfig`.
   *
   * Deprecated: use `live`, with a `StatsdClient` such as the one from `statsdUDP`.
   */
  @deprecated("Use zio.metrics.connectors.datadog.live instead", "2.4.0")
  lazy val datadogLayer: ZLayer[DatadogConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[DatadogConfig]
        clt     <- StatsdClient.make.provideSome[Scope](ZLayer.succeed(StatsdConfig(config.host, config.port)))
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener = new DataDogListener(queue)
        _       <- Unsafe.unsafe(unsafe =>
                     ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)(unsafe)))(_ =>
                       ZIO.succeed(MetricClient.removeListener(listener)(unsafe)),
                     ),
                   )
        _       <- DataDogEventProcessor.make(clt, queue)
        _       <- MetricsClient.make(datadogHandler(clt, config))
      } yield (),
    )

  /**
   * Starts the Datadog pipeline. It queues histogram values for sending as distributions, and it runs the poll loop that
   * sends the other kinds of metric. Requires a `StatsdClient`, a `DatadogPublisherConfig` and a `MetricsConfig`.
   *
   * {{{
   * import java.time.Duration
   *
   * import zio._
   *
   * import zio.metrics.connectors._
   * import zio.metrics.connectors.datadog._
   * import zio.metrics.connectors.statsd._
   *
   * val metrics: MetricsConfig = MetricsConfig(Duration.ofSeconds(10))
   * val publisher              = DatadogPublisherConfig(prefix = Some("myapp."))
   * val statsdConfig           = StatsdConfig("localhost", 8125)
   *
   * val datadogPipeline: ZLayer[Any, Nothing, Unit] =
   *   ZLayer.make[Unit](
   *     ZLayer.succeed(metrics),
   *     ZLayer.succeed(publisher),
   *     ZLayer.succeed(statsdConfig),
   *     statsdUDP,
   *     live,
   *   )
   * }}}
   */
  lazy val live: ZLayer[StatsdClient & DatadogPublisherConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[DatadogPublisherConfig]
        clt     <- ZIO.service[StatsdClient]
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener = new DataDogListener(queue)
        _       <- Unsafe.unsafe(unsafe =>
                     ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)(unsafe)))(_ =>
                       ZIO.succeed(MetricClient.removeListener(listener)(unsafe)),
                     ),
                   )
        _       <- DataDogEventProcessor.make(clt, queue, config)
        _       <- MetricsClient.make(datadogHandler(clt, config))
      } yield (),
    )

  private def eventFilter(config: DatadogPublisherConfig): MetricEvent => Boolean =
    if (config.sendUnchanged) {
      !_.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]
    } else
      {
        case MetricEvent.Unchanged(_, _, _) => false
        case e                              => !e.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]
      }

  @deprecated("Use the overload that accepts DatadogPublisherConfig instead", "2.4.0")
  private[connectors] def datadogHandler(
    client: StatsdClient,
    config: DatadogConfig,
  ): Iterable[MetricEvent] => UIO[Unit] = datadogHandler(client, DatadogConfig.toPublisherConfig(config))

  private[connectors] def datadogHandler(
    client: StatsdClient,
    config: DatadogPublisherConfig,
  ): Iterable[MetricEvent] => UIO[Unit] = events => {
    val encoder = DatadogEncoder.encoder(config)

    val send = ZIO
      .foreachDiscard(events.filter(eventFilter(config)))(evt =>
        for {
          encoded <- encoder(evt).catchAll(_ => ZIO.succeed(Chunk.empty))
          _       <- ZIO.when(encoded.nonEmpty)(ZIO.attempt(client.send(encoded)))
        } yield (),
      )

    send.ignore
  }

}
