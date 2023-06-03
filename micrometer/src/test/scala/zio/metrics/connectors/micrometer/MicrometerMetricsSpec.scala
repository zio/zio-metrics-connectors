package zio.metrics.connectors.micrometer

import java.time.Instant

import scala.jdk.CollectionConverters._

import zio.{durationInt, Chunk, ZIO, ZLayer}
import zio.metrics.{Metric, MetricKey, MetricKeyType}
import zio.test.{assertTrue, ZIOSpecDefault}
import zio.test.TestAspect.{timed, timeoutWarning}

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.distribution.CountAtBucket
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

object MicrometerMetricsSpec extends ZIOSpecDefault {

  override def spec = suite("The Micrometer registry should")(
    testCounter,
    testGauge,
    testHistogram,
    testSummary,
    testFrequency,
  ).provide(
    micrometerLayer,
    ZLayer.succeed(new SimpleMeterRegistry()),
    ZLayer.succeed(MicroMeterConfig.default),
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

    testMetric(MetricKey.histogram(name, buckets))(testData).map(searchResult =>
      assertTrue {
        val resultSummary = searchResult.summary().takeSnapshot()
        resultSummary.count() == testData.size &&
        resultSummary.total() == testData.sum &&
        resultSummary.max() == testData.max &&
        resultSummary.histogramCounts().toSeq == buckets.values
          .map(bound => new CountAtBucket(bound, testData.count(_ <= bound)))
          .toSeq
      },
    )
  }

  private val testSummary = test("catch zio summary updates") {
    val name        = "testSummary"
    val now         = Instant.now()
    val testData    = Chunk.fromIterable(1 to 1000).map(i => i.toDouble -> now)
    val percentiles = Chunk(0.1, 0.5, 0.9)
    testMetric(MetricKey.summary(name, 1.day, 100, 0.03d, percentiles))(testData).map(searchResult =>
      assertTrue {
        val resultSummary = searchResult.summary().takeSnapshot()
        resultSummary.count() == testData.size &&
        resultSummary.total() == testData.map(_._1).sum &&
        resultSummary.max() == testData.map(_._1).max &&
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
          )
      },
    )
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
      assertTrue(checkBucketCounter(bucketCounters, "Sample1") && checkBucketCounter(bucketCounters, "Sample2"))
    }
  }

}
