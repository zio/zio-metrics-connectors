package zio.metrics.connectors.datadog

import zio._
import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics._
import zio.metrics.connectors.MetricEvent
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.time.Instant

object DatadogEncoderSpec extends ZIOSpecDefault {

  override def spec = suite("The Datadog encoder should")(
    sendHistogram,
    sendHistograms,
    encodeContainerId,
    encodeHistogramWithContainerId
  ) @@ timed @@ timeoutWarning(60.seconds)

  private val sendHistogram = test("send histogram update") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogConfig.default)
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0)).toArray)
    assert(encoded)(equalTo(s"$name:1|d|#$tagName:$tagValue"))
  }

  private val sendHistograms = test("send histogram updates") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogConfig.default)
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assert(encoded)(equalTo(s"$name:1:2|d|#$tagName:$tagValue"))
  }

  private val encodeContainerId = test("encode container ID") {
    val containerId = "aaa"
    val name        = "m1"
    val encoder     = DatadogEncoder.encoder(DatadogConfig.default.copy(containerId = Some(containerId)))
    val event       = MetricEvent.New(MetricKey.gauge(name), MetricState.Gauge(1), Instant.now())
    for {
      encoded <- encoder(event)
      s       = new String(encoded.toArray)
    } yield assert(s)(equalTo(s"$name:1|g|c:$containerId"))
  }

  private val encodeHistogramWithContainerId = test("encode histogram with container ID") {
    val containerId = "aaa"
    val name        = "testHistogram"
    val tagName     = "testTag"
    val tagValue    = "tagValue"
    val encoder     = DatadogEncoder.histogramEncoder(DatadogConfig.default.copy(containerId = Some(containerId)))
    val key         = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded     = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assert(encoded)(equalTo(s"$name:1:2|d|#$tagName:$tagValue|c:$containerId"))
  }

}
