package zio.metrics.connectors.datadog

import java.text.DecimalFormat

import zio._
import zio.metrics._

case object DatadogEncoder {

  private val BUF_PER_METRIC = 128

  def encodeHistogramValues(
    key: MetricKey[MetricKeyType.Histogram],
    values: NonEmptyChunk[Double],
  ): Task[Chunk[Byte]] = {
    val result = new StringBuilder(BUF_PER_METRIC)

    appendMetric(result, key.name, values, "d", key.tags)

    ZIO.attempt(Chunk.fromArray(result.toString().getBytes()))
  }

  private def appendMetric(
    buf: StringBuilder,
    name: String,
    values: NonEmptyChunk[Double],
    metricType: String,
    tags: Set[MetricLabel],
    extraTags: MetricLabel*,
  ): StringBuilder = {
    val tagBuf      = new StringBuilder()
    val withTags    = appendTags(tagBuf, tags)
    val withAllTags = appendTags(withTags, extraTags)

    val withLF = if (buf.nonEmpty) buf.append("\n") else buf

    val withMetric = withLF
      .append(name)
      .append(":")

    buf.append(format.format(values.head))

    values.tail.foreach(value => buf.append(",").append(format.format(value)))

    buf
      .append("|")
      .append(metricType)

    if (withAllTags.nonEmpty) {
      withMetric.append("|#").append(tagBuf)
    } else withMetric
  }

  private def appendTag(buf: StringBuilder, tag: MetricLabel): StringBuilder = {
    if (buf.nonEmpty) buf.append(",")
    buf.append(tag.key).append(":").append(tag.value)
  }

  private def appendTags(buf: StringBuilder, tags: Iterable[MetricLabel]): StringBuilder =
    tags.foldLeft(buf) { case (cur, tag) => appendTag(cur, tag) }

  private lazy val format = new DecimalFormat("0.################")

}
