package zio.metrics.connectors.micrometer

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import scala.jdk.CollectionConverters._

import zio.{Unsafe, URIO, ZIO}
import zio.metrics.{MetricKey, MetricKeyType, MetricListener}
import zio.metrics.MetricKeyType.{Counter, Frequency, Gauge}
import zio.metrics.connectors.micrometer.internal.AtomicDouble

import io.micrometer.core.instrument.{DistributionSummary, MeterRegistry, Tag}

private[micrometer] class MicrometerMetricListener(
                                                    meterRegistry: MeterRegistry,
                                                    config: MicrometerConfig,
                                                    activeGauges: ConcurrentMap[MetricKey.Gauge, AtomicDouble])
    extends MetricListener {
  private def getOrCreateGaugeRef(key: MetricKey[Gauge]): AtomicDouble =
    activeGauges.computeIfAbsent(
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
      .percentilePrecision(config.summaryPercentileDigitsOfPrecision)
      .register(meterRegistry)
      .record(value)

  override def updateCounter(key: MetricKey[Counter], value: Double)(implicit unsafe: Unsafe): Unit =
    meterRegistry
      .counter(key.name, micrometerTags(key.tags).asJava)
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
