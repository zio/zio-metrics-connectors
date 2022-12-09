package zio.metrics.connectors.datadog

import zio._
import zio.internal.RingBuffer
import zio.metrics._
import zio.metrics.connectors.statsd.StatsdClient

object DataDogEventProcessor {

  def make(
    client: StatsdClient,
    queue: RingBuffer[(MetricKey[MetricKeyType.Histogram], Double)],
  ): ZIO[DatadogConfig, Nothing, Unit] =
    ZIO.service[DatadogConfig].flatMap { config =>
      ZIO
        .attempt {
          while (!queue.isEmpty()) {
            val items  = queue.pollUpTo(config.maxBatchedMetrics)
            val values = items.groupMap(_._1)(_._2)
            values.foreach { case (key, value) =>
              val encoded = DatadogEncoder.encodeHistogramValues(key, NonEmptyChunk.fromChunk(value).get)
              client.send(encoded)
            }
          }
        }
        .ignoreLogged
        .schedule(Schedule.fixed(config.metricProcessingInterval))
        .forkDaemon
        .unit
    }
}
