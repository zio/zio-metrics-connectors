package zio.metrics.connectors.prometheus

import java.time.Instant

import zio._
import zio.metrics._
import zio.metrics.connectors._

case object PrometheusEncoder {

  def encode(event: MetricEvent): Task[Chunk[String]] =
    ZIO.attempt(unsafeEncodeMetric(event.metricKey, event.current, event.timestamp))

  private[zio] def unsafeEncode(event: MetricEvent): Chunk[String] =
    unsafeEncodeMetric(event.metricKey, event.current, event.timestamp)

  private def unsafeEncodeMetric(
    key: MetricKey.Untyped,
    state: MetricState.Untyped,
    timestamp: Instant,
  ): Chunk[String] = {
    val name = key.name.replaceAll("[- \\.]", "_").trim

    // The header required for all Prometheus metrics
    val prometheusType =
      state match {
        case _: MetricState.Counter   => "counter"
        case _: MetricState.Gauge     => "gauge"
        case _: MetricState.Histogram => "histogram"
        case _: MetricState.Summary   => "summary"
        case _: MetricState.Frequency => "counter"
      }

    val encodeHead = {
      val description = key.description.fold(ifEmpty = "")(d => s" $d")
      Chunk(
        s"# TYPE $name $prometheusType",
        s"# HELP $name$description",
      )
    }

    val encodeTimestamp = String.valueOf(timestamp.toEpochMilli)

    def encodeLabels(allLabels: Set[MetricLabel]): String =
      if (allLabels.isEmpty) ""
      else {
        var notFirstLoop = false
        val sb           = new java.lang.StringBuilder(256)
        sb.append("{")
        val iterator     = allLabels.iterator
        while (iterator.hasNext) {
          val l = iterator.next()
          if (notFirstLoop) sb.append(",")
          notFirstLoop = true
          sb.append(l.key).append("=\"").append(l.value).append("\"")
        }
        sb.append("}")
        sb.toString
      }

    val baseLabels = encodeLabels(key.tags)

    def encodeExtraLabels(extraLabels: Set[MetricLabel]) =
      if (extraLabels.isEmpty) baseLabels else encodeLabels(key.tags ++ extraLabels)

    def encodeCounter(c: MetricState.Counter, extraLabels: MetricLabel*): String =
      s"$name${encodeExtraLabels(extraLabels.toSet)} ${c.count} $encodeTimestamp"

    def encodeGauge(g: MetricState.Gauge): String =
      s"$name$baseLabels ${g.value} $encodeTimestamp"

    def encodeHistogram(h: MetricState.Histogram): Chunk[String] =
      encodeSamples(sampleHistogram(h), suffix = "_bucket")

    def encodeSummary(s: MetricState.Summary): Chunk[String] =
      encodeSamples(sampleSummary(s), suffix = "")

    def encodeSamples(samples: SampleResult, suffix: String): Chunk[String] =
      Chunk(
        samples.buckets
          .foldLeft(new java.lang.StringBuilder(samples.buckets.size * 100)) { case (sb, (l, v)) =>
            sb.append(name)
              .append(suffix)
              .append(encodeExtraLabels(l))
              .append(" ")
              .append(v.fold(ifEmpty = "NaN")(String.valueOf))
              .append(" ")
              .append(encodeTimestamp)
              .append("\n")
          }
          .toString,
        s"${name}_sum$baseLabels ${samples.sum} $encodeTimestamp",
        s"${name}_count$baseLabels ${samples.count} $encodeTimestamp",
        s"${name}_min$baseLabels ${samples.min} $encodeTimestamp",
        s"${name}_max$baseLabels ${samples.max} $encodeTimestamp",
      )

    def sampleHistogram(h: MetricState.Histogram): SampleResult =
      SampleResult(
        count = h.count.doubleValue(),
        sum = h.sum,
        min = h.min,
        max = h.max,
        buckets = h.buckets.sortBy(_._1).map { case (le, v) =>
          val label = if (le == Double.MaxValue) "+Inf" else String.valueOf(le)
          (
            Set(MetricLabel("le", label)),
            Some(v.doubleValue()),
          )
        },
      )

    def sampleSummary(s: MetricState.Summary): SampleResult =
      SampleResult(
        count = s.count.doubleValue(),
        sum = s.sum,
        min = s.min,
        max = s.max,
        buckets = s.quantiles.map(q =>
          Set(MetricLabel("quantile", String.valueOf(q._1)), MetricLabel("error", String.valueOf(s.error))) -> q._2,
        ),
      )

    def encodeDetails: Chunk[String] = state match {
      case c: MetricState.Counter   => Chunk(encodeCounter(c))
      case g: MetricState.Gauge     => Chunk(encodeGauge(g))
      case h: MetricState.Histogram => encodeHistogram(h)
      case s: MetricState.Summary   => encodeSummary(s)
      case s: MetricState.Frequency =>
        Chunk.fromIterable(
          s.occurrences
            .map { o =>
              encodeCounter(MetricState.Counter(o._2.doubleValue()), MetricLabel("bucket", o._1))
            },
        )
    }

    encodeHead ++ encodeDetails
  }

  private case class SampleResult(
    count: Double,
    sum: Double,
    min: Double,
    max: Double,
    buckets: Chunk[(Set[MetricLabel], Option[Double])])
}
