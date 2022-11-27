package zio.metrics.connectors.datadog

import zio._
import zio.internal.RingBuffer
import zio.metrics._
import zio.metrics.connectors.statsd.StatsdClient

object DataDogEventProcessor {

  def make(
    client: StatsdClient,
    queue: RingBuffer[(MetricKey[MetricKeyType.Histogram], Double)],
  ): ZIO[Any, Nothing, Unit] =
    ZIO
      .attemptBlockingIO {
        while (!queue.isEmpty()) {
          val items  = queue.pollUpTo(100)
          val values = items.groupMap(_._1)(_._2)
          values.foreach { case (key, value) =>
            val encoded = DatadogEncoder.encodeHistogramValues(key, NonEmptyChunk.fromChunk(value).get)
            client.send(encoded)
          }
        }
      }
      .schedule(Schedule.fixed(1.millis))
      .ignoreLogged
}
