package zio.metrics.connectors.datadog

import zio.Unsafe
import zio.internal.RingBuffer
import zio.metrics.{MetricKey, MetricKeyType, MetricListener}
import zio.metrics.MetricKeyType.Gauge

/**
 * A `MetricListener` that queues histogram values, for `DataDogEventProcessor` to send. The poll loop sends the other
 * kinds of metric.
 */
class DataDogListener(queue: RingBuffer[(MetricKey[MetricKeyType.Histogram], Double)]) extends MetricListener {

  /** Queues `value` for `key`, to be sent with the next histogram batch. */
  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit = {
    val _ = queue.offer((key, value))
  }

  /** Does nothing. The poll loop sends gauges. */
  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

  /** Does nothing. The poll loop sends gauges. */
  def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

  /** Does nothing. The poll loop sends frequencies. */
  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

  /** Does nothing. The poll loop sends summaries. */
  def updateSummary(
    key: MetricKey[MetricKeyType.Summary],
    value: Double,
    instan: java.time.Instant,
  )(implicit unsafe: Unsafe,
  ): Unit = ()

  /** Does nothing. The poll loop sends counters. */
  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()

}
