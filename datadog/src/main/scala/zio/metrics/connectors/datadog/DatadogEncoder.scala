package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.statsd.StatsdEncoder

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  def encoder(config: DatadogConfig): MetricEvent => Task[Chunk[Byte]] = { event =>
    ZIO.attempt(Chunk.fromArray(addContextTags(StatsdEncoder.encodeEvent(event), config).toString().getBytes()))
  }

  def histogramEncoder(
    config: DatadogConfig,
  ): (MetricKey[MetricKeyType.Histogram], NonEmptyChunk[Double]) => Chunk[Byte] = {

    def encodeHistogramValues(key: MetricKey[MetricKeyType.Histogram], values: NonEmptyChunk[Double]): StringBuilder = {
      val result = new StringBuilder(BUF_PER_METRIC)
      StatsdEncoder.appendMetric(result, key.name, values, "d", key.tags)
    }

    (key, values) => Chunk.fromArray(addContextTags(encodeHistogramValues(key, values), config).toString().getBytes())
  }

  private def addContextTags(s: StringBuilder, config: DatadogConfig): StringBuilder = {
    val withContainerId = config.containerId match {
      case Some(cid) => s.append(cidString(cid))
      case None      => s
    }
    val withEntityId    = config.entityId match {
      case Some(eid) => withContainerId.append(eidString(eid))
      case None      => withContainerId
    }
    withEntityId
  }

  private def cidString(cid: String) = s"|c:$cid"

  private def eidString(eid: String) = s"|dd.internal.entity_id:$eid"
}
