package zio.metrics.connectors

import java.time.Duration

final case class MetricsConfig(
  /**
   * Interval for polling metrics registry.
   */
  interval: Duration,

  /**
   * Key name for MetricLabel used as description (currently in Prometheus connector only).
   */
  descriptionKey: String = "description")
