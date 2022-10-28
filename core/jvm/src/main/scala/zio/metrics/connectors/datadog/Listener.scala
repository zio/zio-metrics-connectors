package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.statsd.StatsdClient
import zio.stream.ZStream

class DataDogListener(client: StatsdClient, queue: Queue[(MetricKey[MetricKeyType.Histogram], Double)])
    extends MetricClient.Listener {
  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double): UIO[Unit]                  =
    queue.offer((key, value)).unit
  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double): UIO[Unit]                          = ZIO.unit
  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String): UIO[Unit]                  = ZIO.unit
  def updateSummary(key: MetricKey[MetricKeyType.Summary], value: (Double, java.time.Instant)): UIO[Unit] = ZIO.unit
  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double): UIO[Unit]                      = ZIO.unit
}

object DataDogListener {

  def make(client: StatsdClient, queue: Queue[(MetricKey[MetricKeyType.Histogram], Double)]): ZIO[Any, Nothing, Unit] =
    ZStream
      .fromQueue(queue)
      .groupedWithin(100, 1.second) // TODO make this configurable
      .map(metrics => metrics.groupMap(_._1)(_._2))
      .mapConcatChunkZIO(metrics =>
        ZIO.foreach(Chunk.fromIterable(metrics)) { case (key, values) =>
          DatadogEncoder.encodeHistogramValues(key, NonEmptyChunk.fromIterableOption(values).get)
        },
      )
      .mapZIO(chunk => ZIO.attempt(client.send(chunk)).ignore)
      .runDrain
      .forkDaemon
      .unit
}
