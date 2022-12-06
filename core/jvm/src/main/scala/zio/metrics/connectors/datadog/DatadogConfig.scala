package zio.metrics.connectors.datadog

import zio._
import zio.{ULayer, ZLayer}

final case class DatadogConfig(
  host: String,
  port: Int,
  maxBatchedMetrics: Int,
  metricProcessingInterval: Duration,
  maxQueueSize: Int)

object DatadogConfig {

  val default: DatadogConfig =
    DatadogConfig(
      host = "localhost",
      port = 8125,
      maxBatchedMetrics = 10,
      metricProcessingInterval = 100.millis,
      maxQueueSize = 100000,
    )

  val defaultLayer: ULayer[DatadogConfig] = ZLayer.succeed(default)
}
