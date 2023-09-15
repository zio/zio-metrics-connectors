package zio.metrics.connectors.prometheus

import java.time.Instant

import zio._
import zio.metrics._
import zio.metrics.connectors._

case object PrometheusEncoder {

  def encode(event: MetricEvent): ZIO[Any, Throwable, Chunk[String]] =
    ZIO.attempt(encodeMetric(event.metricKey, event.current, event.timestamp))

  private def encodeMetric(
    key: MetricKey.Untyped,
    state: MetricState.Untyped,
    timestamp: Instant,
  ): Chunk[String] = {
    val name = key.name.replaceAll("-", "_").trim

    // The header required for all Prometheus metrics
    val prometheusType = state match {
      case _: MetricState.Counter   => "counter"
      case _: MetricState.Gauge     => "gauge"
      case _: MetricState.Histogram => "histogram"
      case _: MetricState.Summary   => "summary"
      case _: MetricState.Frequency => "counter"
    }

    val encodeHead = {
      val description = key.description.fold("")(d => s" $d")
      Chunk(
        s"# TYPE $name $prometheusType",
        s"# HELP $name$description",
      )
    }

    val encodeTimestamp = s"${timestamp.toEpochMilli}"

    def encodeLabels(allLabels: Set[MetricLabel]) =
      (
        if (allLabels.isEmpty) new StringBuilder("")
        else
          allLabels
            .foldLeft(new StringBuilder(256).append("{")) { case (sb, l) =>
              sb.append(l.key).append("=\"").append(l.value).append("\",")
            }
            .append("}")
      ).result()

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
          .foldLeft(new StringBuilder(samples.buckets.size * 100)) { case (sb, (l, v)) =>
            sb.append(name)
              .append(suffix)
              .append(encodeExtraLabels(l))
              .append(" ")
              .append(v.map(_.toString).getOrElse("NaN"))
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
        buckets = h.buckets
          .filter(_._1 != Double.MaxValue)
          .sortBy(_._1)
          .map { s =>
            (
              Set(MetricLabel("le", s"${s._1}")),
              Some(s._2.doubleValue()),
            )
          } :+ (Set(MetricLabel("le", "+Inf")) -> Some(h.count.doubleValue())),
      )

    def sampleSummary(s: MetricState.Summary): SampleResult =
      SampleResult(
        count = s.count.doubleValue(),
        sum = s.sum,
        min = s.min,
        max = s.max,
        buckets = s.quantiles.map(q =>
          Set(MetricLabel("quantile", q._1.toString), MetricLabel("error", s.error.toString)) -> q._2,
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
