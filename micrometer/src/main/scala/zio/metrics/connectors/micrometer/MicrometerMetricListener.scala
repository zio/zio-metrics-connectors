package zio.metrics.connectors.micrometer

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import java.util.function.{Function => JFunction}

import scala.collection.concurrent
import scala.jdk.CollectionConverters._

import zio.{Duration, Unsafe, URIO, ZIO}
import zio.metrics.{MetricKey, MetricKeyType, MetricListener}
import zio.metrics.connectors.micrometer.MicrometerMetricListener.TimeUnitKey
import zio.metrics.connectors.micrometer.internal.AtomicDouble

import io.micrometer.core.instrument.{Counter, DistributionSummary, Gauge => MGauge, MeterRegistry, Tag, Timer}

/**
 * io.micrometer.core.instrument.MeterRegistry#warnAboutDoubleRegistration(String, Meter.Id)
 */

private[micrometer] class MicrometerMetricListener(
  meterRegistry: MeterRegistry,
  config: MicrometerConfig,
  activeGauges: ConcurrentMap[MetricKey.Gauge, AtomicDouble],
  activeTimers: concurrent.Map[MetricKey.Untyped, Timer],
  activeDistributionSummaries: concurrent.Map[MetricKey.Untyped, DistributionSummary])
    extends MetricListener {

  private def useDistributionSummary(
    key: MetricKey.Untyped,
    f: DistributionSummary => Unit,
  )(create: => DistributionSummary,
  ): Unit =
    f(activeDistributionSummaries.getOrElseUpdate(key, create))

  private def useTimer(key: MetricKey.Untyped, f: Timer => Unit)(create: => Timer): Unit =
    f(activeTimers.getOrElseUpdate(key, create))

  private val newGaugeStateFunction: JFunction[MetricKey.Gauge, AtomicDouble] = key => {
    val gaugeState = AtomicDouble.make(0)
    MGauge
      .builder(key.name, gaugeState, (v: AtomicDouble) => v.get())
      .tags(micrometerTags(key.tags).asJava)
      .description(key.description.orNull)
      .strongReference(true)
      .register(meterRegistry)
    gaugeState
  }

  private def getOrCreateGaugeRef(key: MetricKey[MetricKeyType.Gauge]): AtomicDouble =
    activeGauges.computeIfAbsent(key, newGaugeStateFunction)

  override def updateHistogram(
    key: MetricKey[MetricKeyType.Histogram],
    value: Double,
  )(implicit unsafe: Unsafe,
  ): Unit = {
    val timeUnit = key.tags.find(_.key == TimeUnitKey)
    if (timeUnit.isEmpty) {
      val sloBoundaries = key.keyType.boundaries.values.filter(_ > 0).map(_ * config.histogramMultiplicator)
      useDistributionSummary(key, _.record(value)) {
        DistributionSummary
          .builder(key.name)
          .tags(micrometerTags(key.tags).asJava)
          .description(key.description.orNull)
          .scale(config.histogramMultiplicator)
          .serviceLevelObjectives(sloBoundaries: _*)
          .register(meterRegistry)
      }
    } else {
      val chronoUnitNanoDuration = ChronoUnitByNameLower(timeUnit.get.value).getDuration.toNanos
      val totalNanos             = Math.round(value * chronoUnitNanoDuration)
      useTimer(key, _.record(Duration.fromNanos(totalNanos))) {
        val tags          = key.tags -- timeUnit
        val sloBoundaries =
          key.keyType.boundaries.values
            .filter(_ > 0)
            .map(b => Duration.fromNanos(Math.round(b * chronoUnitNanoDuration)))
        Timer
          .builder(key.name)
          .tags(micrometerTags(tags).asJava)
          .description(key.description.orNull)
          .serviceLevelObjectives(sloBoundaries: _*)
          .register(meterRegistry)
      }
    }
  }

  override def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit =
    getOrCreateGaugeRef(key).set(value)

  override def modifyGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit =
    getOrCreateGaugeRef(key).incrementBy(value)

  override def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit =
    Counter
      .builder(key.name)
      .tags((micrometerTags(key.tags) ++ Iterable(Tag.of("bucket", value))).asJava)
      .description(key.description.orNull)
      .register(meterRegistry)
      .increment()

  override def updateSummary(
    key: MetricKey[MetricKeyType.Summary],
    value: Double,
    instant: Instant,
  )(implicit unsafe: Unsafe,
  ): Unit = useDistributionSummary(key, _.record(value)) {
    DistributionSummary
      .builder(key.name)
      .tags(
        (micrometerTags(key.tags) ++ Iterable(Tag.of("error", key.keyType.error.toString))).asJava,
      )
      .description(key.description.orNull)
      .distributionStatisticBufferLength(key.keyType.maxSize)
      .distributionStatisticExpiry(key.keyType.maxAge)
      .publishPercentiles(key.keyType.quantiles: _*)
      .percentilePrecision(config.summaryPercentileDigitsOfPrecision)
      .register(meterRegistry)
  }

  override def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit =
    Counter
      .builder(key.name)
      .tags(micrometerTags(key.tags).asJava)
      .description(key.description.orNull)
      .register(meterRegistry)
      .increment(value)
}

object MicrometerMetricListener {

  private val TimeUnitKey = "time_unit"

  private[micrometer] def make: URIO[MeterRegistry with MicrometerConfig, MicrometerMetricListener] =
    for {
      meterRegistry <- ZIO.service[MeterRegistry]
      config        <- ZIO.service[MicrometerConfig]
    } yield new MicrometerMetricListener(
      meterRegistry,
      config,
      new ConcurrentHashMap[MetricKey.Gauge, AtomicDouble],
      new ConcurrentHashMap().asScala,
      new ConcurrentHashMap().asScala,
    )
}
