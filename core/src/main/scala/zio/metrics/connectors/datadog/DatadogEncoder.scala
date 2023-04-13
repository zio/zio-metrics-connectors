package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.statsd.StatsdEncoder

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  def encoder(config: DatadogConfig): MetricEvent => Task[Chunk[Byte]] = {
    val base            = StatsdEncoder.encodeEvent _
    val withContainerId = config.containerId match {
      case Some(cid) =>
        val s = cidString(cid)
        (event: MetricEvent) => base(event).append(s)
      case None      =>
        base
    }
    event => ZIO.attempt(Chunk.fromArray(withContainerId(event).toString().getBytes()))
  }

  def histogramEncoder(
    config: DatadogConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] = {

    def encodeHistogramValues(key: MetricKey[MetricKeyType.Histogram], values: NonEmptyChunk[Double]): StringBuilder = {
      val result = new StringBuilder(BUF_PER_METRIC)
      StatsdEncoder.appendMetric(result, key.name, values, "d", key.tags)
    }

    val base            = encodeHistogramValues _
    val withContainerId = config.containerId match {
      case Some(cid) =>
        val s = cidString(cid)
        (key: MetricKey[MetricKeyType.Histogram], values: NonEmptyChunk[Double]) => base(key, values).append(s)
      case None      =>
        base
    }
    (key, values) => Chunk.fromArray(withContainerId(key, values).toString().getBytes())
  }

  private def cidString(cid: String) = s"|c:$cid"
}
