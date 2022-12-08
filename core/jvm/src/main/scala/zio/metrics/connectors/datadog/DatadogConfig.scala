package zio.metrics.connectors.datadog

import zio._
import zio.{ULayer, ZLayer}

final case class DatadogConfig(
  host: String,
  port: Int,
  maxBatchedMetrics: Int = 10,
  metricProcessingInterval: Duration = 100.millis,
  maxQueueSize: Int = 100000)

object DatadogConfig {

  val default: DatadogConfig =
    DatadogConfig(
      host = "localhost",
      port = 8125,
    )

  val defaultLayer: ULayer[DatadogConfig] = ZLayer.succeed(default)
}
