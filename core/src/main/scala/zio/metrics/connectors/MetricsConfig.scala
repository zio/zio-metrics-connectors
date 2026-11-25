package zio.metrics.connectors

import java.time.Duration

/**
 * Settings for the loop that reads the metrics registry and hands the events to a connector. All connectors except
 * Micrometer use it.
 *
 * {{{
 * val config: MetricsConfig = MetricsConfig(java.time.Duration.ofSeconds(10))
 * }}}
 */
final case class MetricsConfig(
  /**
   * How often the metrics registry is read. Each read sends its events to the connector.
   */
  interval: Duration)
