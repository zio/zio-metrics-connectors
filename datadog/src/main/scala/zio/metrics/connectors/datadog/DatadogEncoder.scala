package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.statsd.StatsdEncoder

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  @deprecated("Use the overload that accepts DatadogPublisherConfig instead", "2.4.0")
  def encoder(config: DatadogConfig): MetricEvent => Task[Chunk[Byte]] =
    encoder(DatadogConfig.toPublisherConfig(config))

  def encoder(config: DatadogPublisherConfig): MetricEvent => Task[Chunk[Byte]] = {
    val encoder = makeStatsdEncoder(config)
    event => ZIO.attempt(Chunk.fromArray(encoder.encodeEvent(event).toString().getBytes()))
  }

  @deprecated("Use the overload that accepts DatadogPublisherConfig instead", "2.4.0")
  def histogramEncoder(
    config: DatadogConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] =
    histogramEncoder(DatadogConfig.toPublisherConfig(config))

  def histogramEncoder(
    config: DatadogPublisherConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] = {
    val encoder = makeStatsdEncoder(config)

    def encodeHistogramValues(key: MetricKey[MetricKeyType.Histogram], values: NonEmptyChunk[Double]): StringBuilder = {
      val builder = new java.lang.StringBuilder(BUF_PER_METRIC)
      encoder.appendMetric(
        builder = builder,
        name = key.name,
        values = values,
        metricType = "d",
        tags = key.tags,
      )

      // Scala StringBuilder keep for retro-compatibility as this function is public
      new StringBuilder(underlying = builder)
    }

    (key, values) => Chunk.fromArray(encodeHistogramValues(key, values).toString().getBytes())
  }

  private def makeStatsdEncoder(config: DatadogPublisherConfig): StatsdEncoder =
    StatsdEncoder(
      config.entityId.map(eid => MetricLabel("dd.internal.entity_id", eid)).toList,
      config.containerId.map(cidString),
    )

  private def cidString(cid: String) = s"|c:$cid"
}
