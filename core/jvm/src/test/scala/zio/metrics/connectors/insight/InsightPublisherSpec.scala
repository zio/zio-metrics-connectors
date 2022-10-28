package zio.metrics.connectors.insight

import zio.ZIO
import zio.metrics.{MetricPair, MetricState}
import zio.metrics.connectors.Generators
import zio.test._

object InsightPublisherSpec extends ZIOSpecDefault with Generators {

  def spec = addGauges

  // 1. [x] instantiate InsightPublisher
  // 2. Generate set of metrics
  // 3. write (set) MetricKey and MetricState to InsightPublisher
  // 4. test getAllKeys
  // 5. test getMetrics

  val insightPublisher: ZIO[Any, Nothing, InsightPublisher] = InsightPublisher.make

  private val addGauges = test("Add gauges to InsightPublisher") {
    check(genGauge) { case (pair: MetricPair.Untyped, state: MetricState.Gauge) =>
      for {
        ip     <- insightPublisher
        _      <- ip.set((pair.metricKey, state))
        fromIp <- ip.getMetrics(Seq(pair.metricKey))
      } yield assertTrue(fromIp.states.size == 1) &&
        assertTrue(fromIp.states.head._2.asInstanceOf[MetricState.Gauge] == state)
    }
  }

}
