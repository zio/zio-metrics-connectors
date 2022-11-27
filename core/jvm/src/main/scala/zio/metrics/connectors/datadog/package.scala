package zio.metrics.connectors

import zio.{metrics, _}
import zio.internal.RingBuffer
import zio.metrics.{MetricClient, MetricKey, MetricKeyType}
import zio.metrics.connectors.internal.MetricsClient
import zio.metrics.connectors.statsd.StatsdClient
import zio.metrics.connectors.statsd.StatsdConfig

package object datadog {

  def statsdHandler(client: StatsdClient): Iterable[MetricEvent] => UIO[Unit] = events =>
    statsd.statsdHandler(client)(events.filter(_.metricKey.keyType.isInstanceOf[metrics.MetricKeyType.Histogram]))

  lazy val datadogLayer: ZLayer[StatsdConfig & MetricsConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        clt     <- StatsdClient.make
        queue    = RingBuffer.apply[(MetricKey[MetricKeyType.Histogram], Double)](1000)
        listener = new DataDogListener(clt, queue)
        _       <- ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(listener)(Unsafe.unsafe)))(_ =>
                     ZIO.succeed(MetricClient.removeListener(listener)(Unsafe.unsafe)),
                   )
        _       <- DataDogEventProcessor.make(clt, queue)
        _       <- MetricsClient.make(statsdHandler(clt))
      } yield (),
    )
}
