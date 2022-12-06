package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.statsd.StatsdEncoder

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  def encodeHistogramValues(
    key: MetricKey[MetricKeyType.Histogram],
    values: NonEmptyChunk[Double],
  ): Chunk[Byte] = {
    val result = new scala.collection.mutable.StringBuilder(BUF_PER_METRIC)

    StatsdEncoder.appendMetric(result, key.name, values, "d", key.tags)

    Chunk.fromArray(result.toString().getBytes())
  }
}
