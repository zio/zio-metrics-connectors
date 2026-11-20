package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.statsd.StatsdEncoder

/**
 * Encodes metrics for the Datadog StatsD agent. Compared with `StatsdEncoder`, it adds the entity-ID tag and the container
 * suffix. `histogramEncoder` encodes histogram values as distributions (type `d`).
 */
case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  /**
   * Deprecated: use the overload that takes `DatadogPublisherConfig`. Same as that overload, with the settings of `config`.
   */
  @deprecated("Use the overload that accepts DatadogPublisherConfig instead", "2.4.0")
  def encoder(config: DatadogConfig): MetricEvent => Task[Chunk[Byte]] =
    encoder(DatadogConfig.toPublisherConfig(config))

  /**
   * Returns a function that encodes one `MetricEvent` as StatsD text, with the settings of `config`. The function fails
   * with a `Throwable` when encoding throws. The Datadog pipeline does not send histogram events through this function;
   * `histogramEncoder` sends them instead.
   */
  def encoder(config: DatadogPublisherConfig): MetricEvent => Task[Chunk[Byte]] = {
    val encoder = makeStatsdEncoder(config)
    event => ZIO.attempt(Chunk.fromArray(encoder.encodeEvent(event).toString().getBytes()))
  }

  /**
   * Deprecated: use the overload that takes `DatadogPublisherConfig`. Same as that overload, with the settings of `config`.
   */
  @deprecated("Use the overload that accepts DatadogPublisherConfig instead", "2.4.0")
  def histogramEncoder(
    config: DatadogConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] =
    histogramEncoder(DatadogConfig.toPublisherConfig(config))

  /**
   * Returns a function that encodes a metric key and one or more observed values as a StatsD distribution (type `d`).
   * The values follow the name, separated by colons. For example, with the prefix `app.`, the constant tag `env:prod`,
   * the entity ID `ent1` and the container ID `cid1`, the key `lat` and the values 1.5 and 2 give
   * `app.lat:1.5:2|d|#env:prod,dd.internal.entity_id:ent1|c:cid1`.
   */
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
      constantTags =
        config.constantTags ++ config.entityId.map(eid => MetricLabel("dd.internal.entity_id", eid)).toList,
      suffix = config.containerId.map(cidString),
      prefix = config.prefix,
    )

  private def cidString(cid: String) = s"|c:$cid"
}
