package zio.metrics.connectors.datadog

import java.time.Duration

import zio.{ULayer, ZLayer}
import zio.metrics.MetricLabel

/**
 * Datadog specific configuration. Deprecated: use [[DatadogPublisherConfig]] instead.
 *
 * @param host
 *  Agent host name
 * @param port
 *  Agent port
 * @param histogramSendInterval
 *  How often the queued distributions are sent, instead of `MetricsConfig.interval`. When not set, that interval is used.
 * @param maxBatchedMetrics
 *  The maximum number of queued values taken in one batch. Each batch is grouped by metric key, and each group is sent as one message
 * @param maxQueueSize
 *  The capacity of the queue of histogram values. The queue is a fixed-size buffer, so this value bounds its memory use
 * @param containerId
 *  An optional Docker container ID, written as a `|c:` suffix on each metric line
 * @param entityId
 *  An optional entity ID, sent as the `dd.internal.entity_id` tag on every metric
 * @param sendUnchanged
 *  Whether metrics that did not change since the last poll are sent as well
 */
@deprecated("Use zio.metrics.connectors.datadog.DatadogPublisherConfig instead", "2.4.0")
final case class DatadogConfig(
  host: String,
  port: Int,
  histogramSendInterval: Option[Duration] = None,
  maxBatchedMetrics: Int = 10,
  maxQueueSize: Int = 100000,
  containerId: Option[String] = None,
  entityId: Option[String] = None,
  sendUnchanged: Boolean = false)

/** Default settings and a layer for [[DatadogConfig]]. */
object DatadogConfig {

  /** The defaults: host `localhost`, port `8125`, batches of 10, a queue of 100000, and no optional settings. */
  val default: DatadogConfig =
    DatadogConfig(
      host = "localhost",
      port = 8125,
      histogramSendInterval = None,
    )

  /** Provides the value of `default`. */
  val defaultLayer: ULayer[DatadogConfig] = ZLayer.succeed(default)

  private[connectors] def toPublisherConfig(config: DatadogConfig): DatadogPublisherConfig =
    DatadogPublisherConfig(
      histogramSendInterval = config.histogramSendInterval,
      maxBatchedMetrics = config.maxBatchedMetrics,
      maxQueueSize = config.maxQueueSize,
      containerId = config.containerId,
      entityId = config.entityId,
      sendUnchanged = config.sendUnchanged,
    )
}

/**
 * Settings for the Datadog publisher, used with `live`.
 *
 * @param histogramSendInterval
 *  How often the queued distributions are sent, instead of `MetricsConfig.interval`. When not set, that interval is used.
 * @param maxBatchedMetrics
 *  The maximum number of queued values taken in one batch. Each batch is grouped by metric key, and each group is sent as one message
 * @param maxQueueSize
 *  The capacity of the queue of histogram values. The queue is a fixed-size buffer, so this value bounds its memory use
 * @param containerId
 *  An optional Docker container ID, written as a `|c:` suffix on each metric line
 * @param entityId
 *  An optional entity ID, sent as the `dd.internal.entity_id` tag on every metric
 * @param sendUnchanged
 *  Whether metrics that did not change since the last poll are sent as well
 * @param constantTags
 *  Tags that are added to every metric that is sent
 * @param prefix
 *  An optional prefix that is prepended to every metric name. Any separator has to be part of the value,
 *  e.g. `Some("myapp.")` turns `myCounter` into `myapp.myCounter`
 */
case class DatadogPublisherConfig(
  histogramSendInterval: Option[Duration] = None,
  maxBatchedMetrics: Int = 10,
  maxQueueSize: Int = 100000,
  containerId: Option[String] = None,
  entityId: Option[String] = None,
  sendUnchanged: Boolean = false,
  constantTags: List[MetricLabel] = Nil,
  prefix: Option[String] = None)
