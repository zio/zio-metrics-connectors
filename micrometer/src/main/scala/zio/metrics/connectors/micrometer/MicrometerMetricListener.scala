package zio.metrics.connectors.micrometer

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import java.util.function.{Function => JFunction}

import scala.jdk.CollectionConverters._

import zio.{Unsafe, URIO, ZIO}
import zio.metrics.{MetricKey, MetricKeyType, MetricLabel, MetricListener}
import zio.metrics.connectors.micrometer.internal.AtomicDouble

import io.micrometer.core.instrument.{Counter, DistributionSummary, Gauge => MGauge, MeterRegistry, Tag}

private[micrometer] class MicrometerMetricListener(
  meterRegistry: MeterRegistry,
  config: MicrometerConfig,
  activeGauges: ConcurrentMap[MetricKey.Gauge, AtomicDouble])
    extends MetricListener {

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
    val baseUnit = key.tags.find(_.key == "time_unit")
    val tags     = key.tags -- baseUnit
    DistributionSummary
      .builder(key.name)
      .tags(micrometerTags(tags).asJava)
      .baseUnit(baseUnit.map(_.value).orNull)
      .description(key.description.orNull)
      .serviceLevelObjectives(key.keyType.boundaries.values.filter(_ > 0): _*) // micrometer prohibits <= 0 slo values
      .register(meterRegistry)
      .record(value)
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
  ): Unit =
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
      .record(value)

  override def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit =
    Counter
      .builder(key.name)
      .tags(micrometerTags(key.tags).asJava)
      .description(key.description.orNull)
      .register(meterRegistry)
      .increment(value)
}

object MicrometerMetricListener {
  private[micrometer] def make: URIO[MeterRegistry with MicrometerConfig, MicrometerMetricListener] =
    for {
      meterRegistry <- ZIO.service[MeterRegistry]
      config        <- ZIO.service[MicrometerConfig]
      activeGauges  <- ZIO.succeed(new ConcurrentHashMap[MetricKey.Gauge, AtomicDouble])
    } yield new MicrometerMetricListener(meterRegistry, config, activeGauges)
}
