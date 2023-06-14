package zio.metrics.connectors

import zio._
import zio.metrics._
import zio.metrics.MetricPair.Untyped
import zio.test._

trait Generators {
  val genPosDouble           = Gen.double(0.0, Double.MaxValue)
  val genNegDouble           = Gen.double(Double.MinValue, 0.0)
  def genSomeDoubles(n: Int) = Gen.chunkOfBounded(1, n)(genPosDouble)

  val genPosLong = Gen.long(0L, Long.MaxValue)

  val nonEmptyString = Gen.alphaNumericString.filter(_.nonEmpty)

  val descriptionKey = "testDescription"

  val genLabel =
    Gen.oneOf(Gen.const(descriptionKey), Gen.alphaNumericString).zipWith(Gen.alphaNumericString)(MetricLabel.apply)

  val genTags = Gen.setOf(genLabel)

  val genCounter = for {
    name  <- nonEmptyString
    tags  <- genTags
    count <- Gen.double(1, 100)
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genCounterNamed(name: String, min: Double = 1.0, max: Double = 100) = for {
    count <- Gen.double(min, max)
    tags  <- genTags
  } yield {
    val state = MetricState.Counter(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genFrequency(count: Int, pastValues: Ref[Set[String]]) = for {
    name        <- nonEmptyString
    tags        <- genTags
    occurrences <- Gen.listOfN(count)(unqiueNonEmptyString(pastValues).flatMap(occName => genPosLong.map(occName -> _)))
  } yield {
    val asMap = occurrences.toMap
    val state = MetricState.Frequency(asMap)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.frequency(name).tagged(tags), state)), state)
  }

  val genFrequency1 = for {
    name    <- nonEmptyString
    tags    <- genTags
    occName <- nonEmptyString
    count   <- genPosLong
  } yield {
    val asMap = Map(occName -> count)
    val state = MetricState.Frequency(asMap)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.frequency(name).tagged(tags), state)), state)
  }

  val genGauge: Gen[Any, (Untyped, MetricState.Gauge)] = for {
    name  <- nonEmptyString
    tags  <- genTags
    count <- genPosDouble
  } yield {
    val state = MetricState.Gauge(count)
    (Unsafe.unsafe(implicit u => MetricPair.make(MetricKey.counter(name).tagged(tags), state)), state)
  }

  def genHistogram = for {
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

  def genSummary = for {
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

  def unqiueNonEmptyString(ref: Ref[Set[String]]) =
    Gen(
      nonEmptyString.sample.filterZIO { s =>
        for {
          exists <- ref.get.map(_.contains(s.value))
          _      <- if (!exists) ref.update(_ + s.value) else ZIO.unit
        } yield !exists
      },
    )

}
