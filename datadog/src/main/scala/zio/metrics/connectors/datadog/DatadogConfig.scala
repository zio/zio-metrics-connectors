package zio.metrics.connectors.datadog

import java.time.Duration

import zio.{ULayer, ZLayer}

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
}
