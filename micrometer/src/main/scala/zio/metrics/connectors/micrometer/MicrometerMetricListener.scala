package zio.metrics.connectors.micrometer

import java.lang.{Double => JDouble}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import scala.jdk.CollectionConverters.IterableHasAsJava

import zio.{Unsafe, ZIO}
import zio.metrics.{MetricKey, MetricKeyType, MetricListener}
import zio.metrics.MetricKeyType.{Counter, Frequency, Gauge}

import io.micrometer.core.instrument.{DistributionSummary, MeterRegistry, Tag}

private[micrometer] class MicrometerMetricListener(meterRegistry: MeterRegistry) extends MetricListener {
  private lazy val gauges: ConcurrentHashMap[MetricKey[Gauge], AtomicDouble] =
    new ConcurrentHashMap[MetricKey[Gauge], AtomicDouble]()

  private def getOrCreateGaugeRef(key: MetricKey[Gauge]): AtomicDouble =
    gauges
      .computeIfAbsent(
        key,
        _ =>
          meterRegistry.gauge(
            key.name,
            micrometerTags(key.tags).asJava,
            AtomicDouble.make(0),
            (v: AtomicDouble) => v.get(),
          ),
      )

  override def updateHistogram(
    key: MetricKey[MetricKeyType.Histogram],
    value: Double,
  )(implicit unsafe: Unsafe,
  ): Unit =
    DistributionSummary
      .builder(key.name)
      .tags(micrometerTags(key.tags).asJava)
      .serviceLevelObjectives(key.keyType.boundaries.values.filter(_ > 0): _*) // micrometer prohibits <= 0 slo values
      .register(meterRegistry)
      .record(value)

  override def updateGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit =
    getOrCreateGaugeRef(key).set(value)

  override def modifyGauge(key: MetricKey[Gauge], value: Double)(implicit unsafe: Unsafe): Unit =
    getOrCreateGaugeRef(key).incrementBy(value)

  override def updateFrequency(key: MetricKey[Frequency], value: String)(implicit unsafe: Unsafe): Unit =
    meterRegistry
      .counter(
        key.name,
        (micrometerTags(key.tags) ++ Iterable(Tag.of("bucket", value))).asJava,
      )
      .increment()

  override def updateSummary(
    key: MetricKey[MetricKeyType.Summary],
    value: Double,
    instant: Instant,
  )(implicit unsafe: Unsafe,
  ): Unit =
    DistributionSummary
      .builder(key.name)
      .tags(
        (micrometerTags(key.tags) ++ Iterable(Tag.of("error", key.keyType.error.toString))).asJava,
      )
      .distributionStatisticBufferLength(key.keyType.maxSize)
      .distributionStatisticExpiry(key.keyType.maxAge)
      .publishPercentiles(key.keyType.quantiles: _*)
      .percentilePrecision(3) // should this be added to config?
      .register(meterRegistry)
      .record(value)

  override def updateCounter(key: MetricKey[Counter], value: Double)(implicit unsafe: Unsafe): Unit =
    meterRegistry
      .counter(key.name, micrometerTags(key.tags).asJava)
      .increment(value)

  /**
   * Scala's `Double` implementation does not play nicely with Java's
   * `AtomicReference.compareAndSwap` as `compareAndSwap` uses Java's `==`
   * reference equality when it performs an equality check. This means that even
   * if two Scala `Double`s have the same value, they will still fail
   * `compareAndSwap` as they will most likely be two, distinct object
   * references. Thus, `compareAndSwap` will fail.
   *
   * This `AtomicDouble` implementation is a workaround for this issue that is
   * backed by an `AtomicLong` instead of an `AtomicReference` in which the
   * Double's bits are stored as a Long value. This approach also reduces boxing
   * and unboxing overhead that can be incurred with `AtomicReference`.
   */
  final private class AtomicDouble private (private val ref: AtomicLong) {

    def get(): Double =
      JDouble.longBitsToDouble(ref.get())

    def set(newValue: Double): Unit =
      ref.set(JDouble.doubleToLongBits(newValue))

    def compareAndSet(expected: Double, newValue: Double): Boolean =
      ref.compareAndSet(JDouble.doubleToLongBits(expected), JDouble.doubleToLongBits(newValue))

    def incrementBy(value: Double): Unit = {
      var loop = true

      while (loop) {
        val current = get()
        loop = !compareAndSet(current, current + value)
      }
    }
  }

  private object AtomicDouble {

    def make(value: Double): AtomicDouble =
      new AtomicDouble(new AtomicLong(JDouble.doubleToLongBits(value)))
  }

}

object MicrometerMetricListener {

  private[micrometer] def make: ZIO[MeterRegistry, Nothing, MicrometerMetricListener] =
    ZIO.serviceWith[MeterRegistry](new MicrometerMetricListener(_))

}
