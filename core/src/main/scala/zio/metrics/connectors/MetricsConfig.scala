package zio.metrics.connectors

import java.time.Duration

final case class MetricsConfig(
  /**
   * Interval for polling metrics registry.
   */
  interval: Duration)
