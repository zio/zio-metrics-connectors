package zio.metrics.connectors

import zio._
import zio.metrics._
import zio.metrics.MetricPair.Untyped
import zio.test._

trait Generators {
  val genPosDouble: Gen[Any, Double]                  = Gen.double(0.0, Double.MaxValue)
  val genNegDouble: Gen[Any, Double]                  = Gen.double(Double.MinValue, 0.0)
  def genSomeDoubles(n: Int): Gen[Any, Chunk[Double]] = Gen.chunkOfBounded(1, n)(genPosDouble)

  val genPosLong: Gen[Any, Long] = Gen.long(0L, Long.MaxValue)

  val nonEmptyString: Gen[Any, String] = Gen.alphaNumericString.filter(_.nonEmpty)

  val descriptionKey = "testDescription"

  val genLabel: Gen[Any, MetricLabel] =
    Gen.oneOf(Gen.const(descriptionKey), Gen.alphaNumericString).zipWith(Gen.alphaNumericString)(MetricLabel.apply)

  val genTags: Gen[Any, Set[MetricLabel]] = Gen.setOf(genLabel)

  val genCounter: Gen[Any, (MetricPair.Untyped, MetricState.Counter)] = for {
    name  <- nonEmptyString
    tags  <- genTags
    count <- Gen.double(1, 100)
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genCounterNamed(
    name: String,
    min: Double = 1.0,
    max: Double = 100,
  ): Gen[Any, (MetricPair.Untyped, MetricState.Counter)] = for {
    count <- Gen.double(min, max)
    tags  <- genTags
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genFrequency(count: Int, pastValues: Ref[Set[String]]): Gen[Any, (MetricPair.Untyped, MetricState.Frequency)] =
    for {
      name        <- nonEmptyString
      tags        <- genTags
      occurrences <-
        Gen.listOfN(count)(uniqueNonEmptyString(pastValues).flatMap(occName => genPosLong.map(occName -> _)))
    } yield {
      val asMap = occurrences.toMap
      val state = MetricState.Frequency(asMap)
      (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.frequency(name).tagged(tags), state)), state)
    }

  val genFrequency1: Gen[Any, (MetricPair.Untyped, MetricState.Frequency)] = for {
    name    <- nonEmptyString
    tags    <- genTags
    occName <- nonEmptyString
    count   <- genPosLong
  } yield {
    val asMap = Map(occName -> count)
    val state = MetricState.Frequency(asMap)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.frequency(name).tagged(tags), state)), state)
  }

  val genGauge: Gen[Any, (MetricPair.Untyped, MetricState.Gauge)] = for {
    name  <- nonEmptyString
    tags  <- genTags
    count <- genPosDouble
  } yield {
    val state = MetricState.Gauge(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genHistogram: Gen[Any, (MetricPair.Untyped, MetricState.Histogram)] = for {
    name  <- nonEmptyString
    tags  <- genTags
    count <- genPosLong
    min   <- Gen.double
    max   <- Gen.double.filter(_ >= min)
    sum    = (min + max)
  } yield {
    val boundaries = MetricKeyType.Histogram.Boundaries.linear(0, 10, 11)
    val buckets    = Chunk(
      (0.0, 0L),
      (1.0, 1L),
      (2.0, 2L),
      (3.0, 3L),
      (4.0, 4L),
      (5.0, 5L),
      (6.0, 6L),
      (7.0, 7L),
      (8.0, 8L),
      (9.0, 9L),
      (10.0, 10L),
      (Double.MaxValue, 10L),
    )
    val state      = MetricState.Histogram(buckets, count, min, max, sum)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.histogram(name, boundaries).tagged(tags), state)), state)
  }

  def genSummary: Gen[Any, (MetricPair.Untyped, MetricState.Summary)] = for {
    name      <- nonEmptyString
    tags      <- genTags
    count     <- genPosLong
    min       <- Gen.double
    max       <- Gen.double.filter(_ >= min)
    sum        = min + max
    error     <- Gen.double(0, 0.1)
    maxAge    <- Gen.finiteDuration
    maxSize   <- Gen.int(1, 100)
    quantiles <- Gen.chunkOfBounded(1, 10)(Gen.double(0, 1) zip Gen.option(genPosDouble))
  } yield {
    val state = MetricState.Summary(error, quantiles, count, min, max, sum)
    (
      Unsafe.unsafe(implicit u =>
        MetricPair.make(MetricKey.summary(name, maxAge, maxSize, error, quantiles.map(_._1)).tagged(tags), state),
      ),
      state,
    )
  }

  def uniqueNonEmptyString(ref: Ref[Set[String]]): Gen[Any, String] =
    Gen(
      nonEmptyString.sample.filterZIO { sample =>
        for {
          exists <- ref.get.map(_.contains(sample.value))
          _      <- ZIO.when(!exists)(ref.update(_ + sample.value))
        } yield !exists
      },
    )
}
