package zio.metrics.connectors.datadog

import zio.Unsafe
import zio.internal.RingBuffer
import zio.metrics.MetricKeyType.Gauge
import zio.metrics.{MetricKey, MetricKeyType, MetricListener}
import zio.metrics.MetricKeyType.Gauge

class DataDogListener(queue: RingBuffer[(MetricKey[MetricKeyType.Histogram], Double)]) extends MetricListener {
  def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit = {
    val _ = queue.offer((key, value))
  }

  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

  def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

  def updateSummary(
    key: MetricKey[MetricKeyType.Summary],
    value: Double,
    instan: java.time.Instant,
  )(implicit unsafe: Unsafe,
  ): Unit = ()

  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()

}
