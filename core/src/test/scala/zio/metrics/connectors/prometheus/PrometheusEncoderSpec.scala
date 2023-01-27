package zio.metrics.connectors.prometheus

import zio._
import zio.metrics.connectors._
import zio.metrics.connectors.MetricEvent._
import zio.test._
import zio.test.TestAspect._

object PrometheusEncoderSpec extends ZIOSpecDefault with Generators {

  override def spec = suite("The Prometheus encoding should")(
    encodeCounter,
    encodeGauge,
    encodeFrequency,
    encodeSummary,
    encodeHistogram,
  ) @@ timed @@ timeoutWarning(60.seconds) @@ parallel @@ withLiveClock

  private val encodeCounter = test("Encode a Counter")(check(genCounter) { case (pair, state) =>
    for {
      timestamp <- ZIO.clockWith(_.instant)
      text      <- PrometheusEncoder.encode(New(pair.metricKey, state, timestamp))
      name       = pair.metricKey.name
    } yield assertTrue(
      text == Chunk(
        s"# TYPE $name counter",
        s"# HELP $name Some help",
        s"$name ${state.count} ${timestamp.toEpochMilli}",
      ),
    )
  })

  private val encodeGauge = test("Encode a Gauge")(check(genGauge) { case (pair, state) =>
    for {
      timestamp <- ZIO.clockWith(_.instant)
      text      <- PrometheusEncoder.encode(New(pair.metricKey, state, timestamp))
      name       = pair.metricKey.name
    } yield assertTrue(
      text == Chunk(
        s"# TYPE $name gauge",
        s"# HELP $name Some help",
        s"$name ${state.value} ${timestamp.toEpochMilli}",
      ),
    )
  })

  private val encodeFrequency = test("Encode a Frequency")(check(genFrequency1) { case (pair, state) =>
    for {
      timestamp <- ZIO.clockWith(_.instant)
      text      <- PrometheusEncoder.encode(New(pair.metricKey, state, timestamp))
      name       = pair.metricKey.name
      expected   = Chunk.fromIterable(state.occurrences).flatMap { case (k, v) =>
                     Chunk(
                       s"# TYPE $name counter",
                       s"# HELP $name Some help",
                       s"""$name{bucket="$k"} ${v.toDouble} ${timestamp.toEpochMilli}""",
                     )
                   }
    } yield assertTrue(text == expected)
  })

  private val encodeSummary = test("Encode a Summary")(check(genSummary) { case (pair, state) =>
    for {
      timestamp <- ZIO.clockWith(_.instant)
      text      <- PrometheusEncoder.encode(New(pair.metricKey, state, timestamp))
      name       = pair.metricKey.name
      epochMilli = timestamp.toEpochMilli
    } yield assertTrue(
      text == Chunk(
        s"# TYPE $name summary",
        s"# HELP $name Some help",
      ) ++ state.quantiles.map { case (k, v) =>
        s"""$name{quantile="$k",error="${state.error}"} ${v.getOrElse(Double.NaN)} $epochMilli"""
      } ++ Chunk(
        s"${name}_sum ${state.sum} $epochMilli",
        s"${name}_count ${state.count.toDouble} $epochMilli",
        s"${name}_min ${state.min} $epochMilli",
        s"${name}_max ${state.max} $epochMilli",
      ),
    )
  })

  private val encodeHistogram = test("Encode a Histogram")(check(genHistogram) { case (pair, state) =>
    for {
      timestamp <- ZIO.clockWith(_.instant)
      text      <- PrometheusEncoder.encode(New(pair.metricKey, state, timestamp))
      name       = pair.metricKey.name
      epochMilli = timestamp.toEpochMilli
    } yield assertTrue(
      text == Chunk(
        s"# TYPE $name histogram",
        s"# HELP $name Some help",
      ) ++ state.buckets.filter(_._1 < Double.MaxValue).map { case (k, v) =>
        s"""${name}_bucket{le="$k"} ${v.toDouble} $epochMilli"""
      } ++ Chunk(
        s"""${name}_bucket{le="+Inf"} ${state.count.toDouble} $epochMilli""",
        s"${name}_sum ${state.sum} $epochMilli",
        s"${name}_count ${state.count.toDouble} $epochMilli",
        s"${name}_min ${state.min} $epochMilli",
        s"${name}_max ${state.max} $epochMilli",
      ),
    )
  })
}
