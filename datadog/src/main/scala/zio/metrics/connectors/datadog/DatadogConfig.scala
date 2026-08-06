package zio.metrics.connectors.datadog

import java.time.Duration

import zio.{ULayer, ZLayer}
import zio.metrics.MetricLabel

/**
 * Datadog Specific configuration
 *
 * @param host
 *  Agent host name
 * @param port
 *  Agent port
 * @param histogramSendInterval
 *  Override for when the distributions should be sent faster than the general metrics frequency.
 *  This is typically with an app that generates lots of distributions, but doesn't want to send other metrics
 *  types, such as gauges, too frequently
 * @param maxBatchedMetrics
 *  The maximum number of metrics to batch before sending. This affects packet size
 * @param maxQueueSize
 *  The maximum number of metrics stored in the queue. This affects memory usage
 * @param containerId
 *  An optional docker container ID
 * @param entityId
 *  An optional entity ID value used with an internal tag for tracking client entity
 */
@deprecated("Use zio.metrics.connectors.DatadogPublisherConfig instead", "2.4.0")
final case class DatadogConfig(
  host: String,
  port: Int,
  histogramSendInterval: Option[Duration] = None,
  maxBatchedMetrics: Int = 10,
  maxQueueSize: Int = 100000,
  containerId: Option[String] = None,
  entityId: Option[String] = None,
  sendUnchanged: Boolean = false)

object DatadogConfig {

  val default: DatadogConfig =
    DatadogConfig(
      host = "localhost",
      port = 8125,
      histogramSendInterval = None,
    )

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
 * Datadog Specific publisher configuration
 *
 * @param histogramSendInterval
 *  Override for when the distributions should be sent faster than the general metrics frequency.
 *  This is typically with an app that generates lots of distributions, but doesn't want to send other metrics
 *  types, such as gauges, too frequently
 * @param maxBatchedMetrics
 *  The maximum number of metrics to batch before sending. This affects packet size
 * @param maxQueueSize
 *  The maximum number of metrics stored in the queue. This affects memory usage
 * @param containerId
 *  An optional docker container ID
 * @param entityId
 *  An optional entity ID value used with an internal tag for tracking client entity
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
