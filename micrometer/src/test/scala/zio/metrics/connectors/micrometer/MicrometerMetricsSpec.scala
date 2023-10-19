package zio.metrics.connectors.micrometer

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.jdk.CollectionConverters._

import zio.{durationInt, Chunk, Scope, ZIO, ZLayer}
import zio.metrics.{Metric, MetricKey, MetricKeyType, MetricLabel}
import zio.test.{assertTrue, Spec, TestEnvironment, ZIOSpecDefault}
import zio.test.TestAspect.{timed, timeoutWarning}

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.distribution.CountAtBucket
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

object MicrometerMetricsSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The Micrometer registry should")(
      testCounter,
      testGauge,
      testHistogram,
      testTimer,
      testSummary,
      testFrequency,
      testDescription,
    ).provide(
      micrometerLayer,
      ZLayer.succeed(new SimpleMeterRegistry()),
      ZLayer.succeed(MicrometerConfig.default),
    ) @@ timed @@ timeoutWarning(60.seconds)

  private def testMetric[Type <: MetricKeyType](
    key: MetricKey[Type],
  )(testValues: Seq[key.keyType.In],
  ) =
    for {
      meterRegistry <- ZIO.service[MeterRegistry]
      _             <- ZIO.foreachDiscard(testValues)(v => ZIO.succeed(v) @@ Metric.fromMetricKey(key).tagged("key", "value"))
    } yield meterRegistry.get(key.name).tags(micrometerTags(key.tags).asJava)

  private val testCounter = test("catch zio counter updates") {
    val name     = "testCounter"
    val testData = Seq(0.5, 5.0)
    testMetric(MetricKey.counter(name))(testData).map(searchResult =>
      assertTrue(searchResult.counter().count() == testData.sum),
    )
  }

  private val testGauge = test("catch zio gauge updates") {
    val name     = "testGauge"
    val testData = Seq(0.5, 5.0)
    testMetric(MetricKey.gauge(name))(testData).map(searchResult =>
      assertTrue(searchResult.gauge().value() == testData.last),
    )
  }

  private val testHistogram = test("catch zio histogram updates") {
    val name     = "testHistogram"
    val testData = Seq(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
    val buckets  = MetricKeyType.Histogram.Boundaries.linear(1, 1, 3)

    testMetric(MetricKey.histogram(name, buckets))(testData).map { searchResult =>
      val summary  = searchResult.summary()
      val snapshot = summary.takeSnapshot()
      assertTrue(
        summary.getId.getBaseUnit eq null,
        snapshot.count() == testData.size.toLong,
        snapshot.total() == testData.sum,
        snapshot.max() == testData.max,
        snapshot.histogramCounts().toList == buckets.values
          .map(bound => new CountAtBucket(bound, testData.count(_ <= bound).toDouble))
          .toList,
      )
    }
  }

  private val testTimer = test("catch zio timer updates") {
    val name     = "testTimer"
    val testData = Chunk(1.0, 2.0, 3.0, 4.0, 5.0)
    val buckets  = MetricKeyType.Histogram.Boundaries.linear(1, 1, 3)
    val tags     = Set(MetricLabel("key", "value"))

    for {
      _       <- ZIO.foreachDiscard(testData)(d =>
                   ZIO.succeed(d.toInt.seconds) @@ Metric.timer(name, ChronoUnit.SECONDS, buckets.values).tagged(tags),
                 )
      summary <- ZIO.serviceWith[MeterRegistry](_.get(name).tags(micrometerTags(tags).asJava).summary())
      snapshot = summary.takeSnapshot()
    } yield assertTrue(
      summary.getId.getBaseUnit == "seconds",
      snapshot.count() == testData.size.toLong,
      snapshot.total() == testData.sum,
      snapshot.max() == testData.max,
      snapshot.histogramCounts().toList == buckets.values
        .map(bound => new CountAtBucket(bound, testData.count(_ <= bound).toDouble))
        .toList,
    )
  }

  private val testSummary = test("catch zio summary updates") {
    val name        = "testSummary"
    val now         = Instant.now()
    val testData    = Chunk.fromIterable(1 to 1000).map(i => i.toDouble -> now)
    val percentiles = Chunk(0.1, 0.5, 0.9)
    testMetric(MetricKey.summary(name, 1.day, 100, 0.03d, percentiles))(testData).map { searchResult =>
      val resultSummary = searchResult.summary().takeSnapshot()
      assertTrue(
        resultSummary.count() == testData.size.toLong,
        resultSummary.total() == testData.map(_._1).sum,
        resultSummary.max() == testData.map(_._1).max,
        resultSummary
          .percentileValues()
          .toSeq
          /* Under the covers, Micrometer uses HdrHistogram to compute percentiles.
          HdrHistogram gives us the opportunity tradeoff computational complexity for accuracy.
          Depends on Percentile precision final percentile value may changed.
          For check purpose we may just round value to Int.
           */
          .map(pv => pv.percentile() -> pv.value().toInt) ==
          Seq(
            0.1 -> 100,
            0.5 -> 500,
            0.9 -> 900,
          ),
      )
    }
  }

  private val testFrequency = test("catch zio frequency updates") {
    val name     = "testFrequency"
    val testData = Seq("Sample1", "Sample2", "Sample1", "Sample1")

    def checkBucketCounter(counters: Iterable[io.micrometer.core.instrument.Counter], value: String): Boolean =
      counters
        .find(_.getId.getTags.asScala.exists(tag => tag.getKey == "bucket" && tag.getValue == value))
        .exists(_.count() == testData.count(_ == value))

    testMetric(MetricKey.frequency(name))(testData).map { searchResult =>
      val bucketCounters = searchResult.counters().asScala
      assertTrue(checkBucketCounter(bucketCounters, "Sample1"), checkBucketCounter(bucketCounters, "Sample2"))
    }
  }

  private val testDescription = test("handle description") {
    val description = "testDescription"

    for {
      gauge     <- testMetric(MetricKey.gauge("testDescGauge", description))(Seq(0)).map(_.gauge())
      counter   <- testMetric(MetricKey.counter("testDescCounter", description))(Seq(0)).map(_.counter())
      histogram <-
        testMetric(
          MetricKey.histogram("testDescHistogram", description, MetricKeyType.Histogram.Boundaries.fromChunk(Chunk(1))),
        )(Seq(0)).map(_.summary())
      summary   <- testMetric(
                     MetricKey.summary("testDescSummary", description, 1.day, 100, 0.03d, Chunk(1)),
                   )(Seq(0d -> Instant.now())).map(_.summary())
      frequency <- testMetric(MetricKey.frequency("testDescFrequency", description))(Seq("str"))
                     .map(_.counter())
    } yield assertTrue(
      gauge.getId.getDescription == description,
      counter.getId.getDescription == description,
      histogram.getId.getDescription == description,
      summary.getId.getDescription == description,
      frequency.getId.getDescription == description,
    )
  }

}
