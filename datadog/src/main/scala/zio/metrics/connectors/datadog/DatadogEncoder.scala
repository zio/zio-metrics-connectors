package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.statsd.StatsdEncoder

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  def encoder(config: DatadogConfig): MetricEvent => Task[Chunk[Byte]] = {
    val encoder = makeStatsdEncoder(config)
    event => ZIO.attempt(Chunk.fromArray(encoder.encodeEvent(event).toString().getBytes()))
  }

  def histogramEncoder(
    config: DatadogConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] = {
    val encoder = makeStatsdEncoder(config)

    def encodeHistogramValues(key: MetricKey[MetricKeyType.Histogram], values: NonEmptyChunk[Double]): StringBuilder = {
      val result = new StringBuilder(BUF_PER_METRIC)
      encoder.appendMetric(result, key.name, values, "d", key.tags)
    }

    (key, values) => Chunk.fromArray(encodeHistogramValues(key, values).toString().getBytes())
  }

  private def makeStatsdEncoder(config: DatadogConfig): StatsdEncoder =
    StatsdEncoder(
      config.entityId.map(eid => MetricLabel("dd.internal.entity_id", eid)).toList,
      config.containerId.map(cidString),
    )

  private def cidString(cid: String) = s"|c:$cid"
}
