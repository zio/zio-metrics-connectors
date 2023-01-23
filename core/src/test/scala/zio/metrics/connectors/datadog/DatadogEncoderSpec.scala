package zio.metrics.connectors.datadog

import zio._
import zio.metrics._
import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.connectors.datadog.DatadogEncoder
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object DatadogEncoderSpec extends ZIOSpecDefault {

  override def spec = suite("The Datadog encoder should")(
    sendHistogram,
    sendHistograms,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private val sendHistogram = test("send histogram update") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(DatadogEncoder.encodeHistogramValues(key, NonEmptyChunk(1.0)).toArray)
    assert(encoded)(equalTo(s"$name:1|d|#$tagName:$tagValue"))
  }

  private val sendHistograms = test("send histogram updates") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(DatadogEncoder.encodeHistogramValues(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assert(encoded)(equalTo(s"$name:1:2|d|#$tagName:$tagValue"))
  }
}
