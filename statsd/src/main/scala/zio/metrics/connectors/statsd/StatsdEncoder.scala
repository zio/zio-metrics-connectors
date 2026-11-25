package zio.metrics.connectors.statsd

import java.text.DecimalFormat

import zio._
import zio.metrics._
import zio.metrics.connectors._

/**
 * Encodes metric events as StatsD text. Each metric value is one line, `name:value|type|#tags`, with the `prefix` in
 * front of the name and the `suffix` at the end of the line.
 *
 * Counters are sent with type `c`. A new counter sends its count, an updated counter sends its change since the
 * previous poll, and an unchanged counter sends `0`. Gauges are sent with type `g`. Histograms, summaries and frequencies
 * are sent as gauges, with an extra tag for each bucket (`le`), quantile (`quantile` and `error`) or observed string
 * (`bucket`).
 *
 * @param constantTags
 *  Tags added to every line, before the metric's own tags
 * @param suffix
 *  An optional text added at the end of each line
 * @param prefix
 *  An optional text added in front of each metric name
 */
case class StatsdEncoder(constantTags: List[MetricLabel], suffix: Option[String], prefix: Option[String] = None) {

  private val BUF_PER_METRIC: Int   = 128
  private val format: DecimalFormat = new DecimalFormat("0.################")

  private val constantTagsFormatted: String =
    constantTags match {
      case Nil => ""
      case _   =>
        val builder = new java.lang.StringBuilder()
        appendTags(builder, constantTags)
        builder.toString
    }

  /**
   * Encodes `event` like `pureEncode`, but inside `ZIO.attempt`, so an exception becomes a failure.
   *
   * Deprecated: use `pureEncode`.
   */
  @deprecated("Use pureEncode instead", "2.5.4")
  def encode(event: MetricEvent): Task[Chunk[Byte]] =
    ZIO.attempt(Chunk.fromArray(encodeEvent(event).toString.getBytes()))

  /**
   * Encodes `event` as the bytes of one datagram. A histogram gives one line per bucket, a summary one line per quantile
   * that has a value, and a frequency one line per observed string, separated by newlines.
   */
  def pureEncode(event: MetricEvent): Chunk[Byte] =
    Chunk.fromArray(encodeEvent(event).toString.getBytes())

  /** Builds the StatsD text of `event`, with one line per value, before it is converted to bytes. */
  def encodeEvent(event: MetricEvent): java.lang.StringBuilder = {
    val builder = new java.lang.StringBuilder(BUF_PER_METRIC)

    event.current match {
      case _: MetricState.Counter   => appendCounter(builder, event)
      case g: MetricState.Gauge     => appendGauge(builder, event.metricKey, g)
      case h: MetricState.Histogram => appendHistogram(builder, event.metricKey, h)
      case s: MetricState.Summary   => appendSummary(builder, event.metricKey, s)
      case f: MetricState.Frequency => appendFrequency(builder, event.metricKey, f)
    }

    builder
  }

  // TODO: We need to determine the delta for the counter since we have last reported it
  // Perhaps we can see the rate for gauges in the backend, so we could report just theses
  // For a counter we only report the last observed value to statsd
  private def appendCounter(builder: java.lang.StringBuilder, event: MetricEvent): Unit = {
    val delta: Double =
      event match {
        case MetricEvent.New(_, current, _)          => current.asInstanceOf[MetricState.Counter].count
        case MetricEvent.Unchanged(_, _, _)          => 0.0d
        case MetricEvent.Updated(_, old, current, _) =>
          current.asInstanceOf[MetricState.Counter].count - old.asInstanceOf[MetricState.Counter].count
      }

    appendMetric(
      builder = builder,
      name = event.metricKey.name,
      values = NonEmptyChunk.single(delta),
      metricType = "c",
      tags = event.metricKey.tags,
    )
  }

  // For a gauge we report the current value to statsd
  private def appendGauge(
    builder: java.lang.StringBuilder,
    key: MetricKey.Untyped,
    g: MetricState.Gauge,
  ): Unit =
    appendMetric(
      builder = builder,
      name = key.name,
      values = NonEmptyChunk.single(g.value),
      metricType = "g",
      tags = key.tags,
    )

  // A Histogram is reported to statsd as a set of related gauges, distinguished by an additional label
  private def appendHistogram(
    builder: java.lang.StringBuilder,
    key: MetricKey.Untyped,
    h: MetricState.Histogram,
  ): Unit =
    h.buckets.foreach { case (boundary, count) =>
      val bucket: String = if (boundary < Double.MaxValue) String.valueOf(boundary) else "Inf"

      appendMetric(
        builder = builder,
        name = key.name,
        values = NonEmptyChunk.single(count.doubleValue()),
        metricType = "g",
        tags = key.tags,
        extraTags = MetricLabel("le", bucket),
      )
    }

  // A Summary is reported to statsd as a set of related gauges, distinguished by an additional label
  // for the quantile and another label for the error margin
  private def appendSummary(
    builder: java.lang.StringBuilder,
    key: MetricKey.Untyped,
    s: MetricState.Summary,
  ): Unit =
    s.quantiles.foreach { case (q, v) =>
      v match {
        case None    => ()
        case Some(v) =>
          appendMetric(
            builder = builder,
            name = key.name,
            values = NonEmptyChunk.single(v),
            metricType = "g",
            tags = key.tags,
            extraTags = List(
              MetricLabel("quantile", String.valueOf(q)),
              MetricLabel("error", String.valueOf(s.error)),
            ): _*,
          )
      }
    }

  // For each individual observed String we are going to report a counter to statsd with an
  // additional label with key "bucket" and the observed String as a value
  private def appendFrequency(
    builder: java.lang.StringBuilder,
    key: MetricKey.Untyped,
    frequency: MetricState.Frequency,
  ): Unit =
    frequency.occurrences.foreach { case (b, c) =>
      appendMetric(
        builder = builder,
        name = key.name,
        values = NonEmptyChunk.single(c.doubleValue()),
        metricType = "g",
        tags = key.tags,
        extraTags = MetricLabel("bucket", b),
      )
    }

  private[connectors] def appendMetric(
    builder: java.lang.StringBuilder,
    name: String,
    values: NonEmptyChunk[Double],
    metricType: String,
    tags: Set[MetricLabel],
    extraTags: MetricLabel*,
  ): Unit = {
    if (!builder.isEmpty) builder.append('\n')

    prefix.foreach(builder.append)

    builder.append(name)
    values.foreach(value => builder.append(':').append(format.format(value)))
    builder.append('|').append(metricType)

    val hasTags = constantTags.nonEmpty || tags.nonEmpty || extraTags.nonEmpty
    if (hasTags) {
      val tagsBuilder = new java.lang.StringBuilder(128)
      tagsBuilder.append(constantTagsFormatted)
      appendTags(tagsBuilder, tags)
      appendTags(tagsBuilder, extraTags)

      builder.append("|#").append(tagsBuilder)
    }

    suffix.foreach(builder.append)
  }

  private def appendTag(builder: java.lang.StringBuilder, tag: MetricLabel): Unit = {
    if (!builder.isEmpty) builder.append(',')
    builder.append(tag.key).append(':').append(tag.value)
    ()
  }

  private def appendTags(builder: java.lang.StringBuilder, tags: Iterable[MetricLabel]): Unit =
    tags.foreach(tag => appendTag(builder, tag))

}

/** The encoder with no constant tags, suffix or prefix. */
object StatsdEncoder extends StatsdEncoder(Nil, None, None)
