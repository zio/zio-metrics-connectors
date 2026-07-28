package zio.metrics.connectors.datadog

import java.time.Instant

import zio._
import zio.metrics._
import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.connectors.MetricEvent
import zio.test._
import zio.test.TestAspect._

object DatadogEncoderSpec extends ZIOSpecDefault {

  override def spec = suite("The Datadog encoder should")(
    sendHistogram,
    sendHistograms,
    encodeContainerId,
    encodeEntityId,
    encodeHistogramWithContainerId,
    encodeHistogramWithEntityId,
    encodePrefix,
    encodeHistogramWithPrefix,
    encodeConstantTags,
    encodeHistogramWithConstantTags,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private val sendHistogram = test("send histogram update") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogPublisherConfig())
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0)).toArray)
    assertTrue(encoded == s"$name:1|d|#$tagName:$tagValue")
  }

  private val sendHistograms = test("send histogram updates") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogPublisherConfig())
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assertTrue(encoded == s"$name:1:2|d|#$tagName:$tagValue")
  }

  private val encodeContainerId = test("encode container ID") {
    val containerId = "aaa"
    val name        = "m1"
    val encoder     = DatadogEncoder.encoder(DatadogPublisherConfig(containerId = Some(containerId)))
    val event       = MetricEvent.New(MetricKey.gauge(name), MetricState.Gauge(1), Instant.now())
    for {
      encoded <- encoder(event)
      s        = new String(encoded.toArray)
    } yield assertTrue(s == s"$name:1|g|c:$containerId")
  }

  private val encodeEntityId = test("encode entity ID") {
    val entityId = "aaa"
    val name     = "m1"
    val encoder  = DatadogEncoder.encoder(DatadogPublisherConfig(entityId = Some(entityId)))
    val event    = MetricEvent.New(MetricKey.gauge(name), MetricState.Gauge(1), Instant.now())
    for {
      encoded <- encoder(event)
      s        = new String(encoded.toArray)
    } yield assertTrue(s == s"$name:1|g|#dd.internal.entity_id:aaa")
  }

  private val encodeHistogramWithContainerId = test("encode histogram with container ID") {
    val containerId = "aaa"
    val name        = "testHistogram"
    val tagName     = "testTag"
    val tagValue    = "tagValue"
    val encoder     = DatadogEncoder.histogramEncoder(DatadogPublisherConfig(containerId = Some(containerId)))
    val key         = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded     = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assertTrue(encoded == s"$name:1:2|d|#$tagName:$tagValue|c:$containerId")
  }

  private val encodeHistogramWithEntityId = test("encode histogram with entity ID") {
    val entityId = "aaa"
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogPublisherConfig(entityId = Some(entityId)))
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assertTrue(encoded == s"$name:1:2|d|#dd.internal.entity_id:aaa,$tagName:$tagValue")
  }

  private val encodePrefix = test("encode prefix") {
    val prefix  = "myapp."
    val name    = "m1"
    val encoder = DatadogEncoder.encoder(DatadogPublisherConfig(prefix = Some(prefix)))
    val event   = MetricEvent.New(MetricKey.gauge(name), MetricState.Gauge(1), Instant.now())
    for {
      encoded <- encoder(event)
      s        = new String(encoded.toArray)
    } yield assertTrue(s == s"$prefix$name:1|g")
  }

  private val encodeHistogramWithPrefix = test("encode histogram with prefix") {
    val prefix   = "myapp."
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(DatadogPublisherConfig(prefix = Some(prefix)))
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assertTrue(encoded == s"$prefix$name:1:2|d|#$tagName:$tagValue")
  }

  private val encodeConstantTags = test("encode constant tags") {
    val name    = "m1"
    val encoder = DatadogEncoder.encoder(
      DatadogPublisherConfig(constantTags = List(MetricLabel("env", "prod"), MetricLabel("service", "api"))),
    )
    val event   = MetricEvent.New(MetricKey.gauge(name), MetricState.Gauge(1), Instant.now())
    for {
      encoded <- encoder(event)
      s        = new String(encoded.toArray)
    } yield assertTrue(s == s"$name:1|g|#env:prod,service:api")
  }

  private val encodeHistogramWithConstantTags = test("encode histogram with constant tags") {
    val name     = "testHistogram"
    val tagName  = "testTag"
    val tagValue = "tagValue"
    val encoder  = DatadogEncoder.histogramEncoder(
      DatadogPublisherConfig(constantTags = List(MetricLabel("env", "prod"))),
    )
    val key      = MetricKey.histogram(name, Boundaries(Chunk.empty)).tagged(tagName, tagValue)
    val encoded  = new String(encoder(key, NonEmptyChunk(1.0, 2.0)).toArray)
    assertTrue(encoded == s"$name:1:2|d|#env:prod,$tagName:$tagValue")
  }

}
