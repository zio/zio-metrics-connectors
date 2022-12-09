package zio.metrics.connectors

import zio.{metrics, _}
import zio.internal.RingBuffer
import zio.metrics.{MetricClient, MetricKey, MetricKeyType}
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.StatsdClient
import zio.metrics.connectors.statsd.StatsdConfig

package object datadog {

  def statsdHandler(client: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events =>
    statsd.statsdHandler(client)(events.filter(!_.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]))

  lazy val datadogLayer: ZLayer[DatadogConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        config  <- ZIO.service[DatadogConfig]
        clt     <- StatsdClient.make.provideSome[Scope](ZLayer.succeed(StatsdConfig(config.host, config.port)))
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](config.maxQueueSize)
        listener = new DataDogListener(clt, queue)
        _       <- Unsafe.unsafe(implicit unsafe =>
                     ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)))(_ =>
                       ZIO.succeed(MetricClient.removeListener(listener)),
                     ),
                   )
        _       <- DataDogEventProcessor.make(clt, queue)
        _       <- MetricsClient.make(statsdHandler(clt))
      } yield (),
    )
}
