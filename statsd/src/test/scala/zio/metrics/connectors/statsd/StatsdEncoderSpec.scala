package zio.metrics.connectors.statsd

import zio._
import zio.metrics._
import zio.metrics.connectors._
import zio.test._
import zio.test.TestAspect._

object StatsdEncoderSpec extends ZIOSpecDefault {

  override def spec = suite("The StatsdEncoder should")(
    sendCounter,
    sendGauge,
    sendCounterWithPrefix,
    sendGaugeWithPrefix,
  ) @@ timed @@ timeoutWarning(60.seconds)

  private def testMetric(k: MetricKey.Untyped, m: MetricState.Untyped) =
    for {
      event  <- ZIO.clockWith(_.instant).map(now => MetricEvent.New(k, m, now))
      encoded = StatsdEncoder.pureEncode(event)
    } yield new String(encoded.toArray)

  private def testMetricWithPrefix(prefix: String, k: MetricKey.Untyped, m: MetricState.Untyped) =
    for {
      event  <- ZIO.clockWith(_.instant).map(now => MetricEvent.New(k, m, now))
      encoder = StatsdEncoder(Nil, None, Some(prefix))
      encoded = encoder.pureEncode(event)
    } yield new String(encoded.toArray)

  private val sendCounter = test("send counter updates") {
    val name = "testCounter"
    testMetric(MetricKey.counter(name), MetricState.Counter(1)).map(res => assertTrue(res.equals(s"$name:1|c")))
  }

  private val sendGauge = test("send gauge updates") {
    val name = "testGauge"
    testMetric(MetricKey.gauge(name), MetricState.Gauge(1)).map(res => assertTrue(res.equals(s"$name:1|g")))
  }

  private val sendCounterWithPrefix = test("send counter updates with prefix") {
    val prefix = "myapp."
    val name   = "testCounter"
    testMetricWithPrefix(prefix, MetricKey.counter(name), MetricState.Counter(1))
      .map(res => assertTrue(res.equals(s"$prefix$name:1|c")))
  }

  private val sendGaugeWithPrefix = test("send gauge updates with prefix") {
    val prefix = "myapp."
    val name   = "testGauge"
    testMetricWithPrefix(prefix, MetricKey.gauge(name), MetricState.Gauge(1))
      .map(res => assertTrue(res.equals(s"$prefix$name:1|g")))
  }
}
