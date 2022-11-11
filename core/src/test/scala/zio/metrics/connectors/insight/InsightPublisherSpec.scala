package zio.metrics.connectors.insight

import zio.ZIO
import zio.metrics.{MetricKey, MetricState}
import zio.metrics.connectors.Generators
import zio.metrics.connectors.MetricEvent.New
import zio.test._

object InsightPublisherSpec extends ZIOSpecDefault with Generators {

  def spec = addCounter

  // 1. [x] instantiate InsightPublisher
  // 2. Generate set of metrics
  // 3. write (set) MetricKey and MetricState to InsightPublisher
  // 4. test getAllKeys
  // 5. test getMetrics

  val insightPublisher: ZIO[Any, Nothing, InsightPublisher] = InsightPublisher.make

  private val addCounter = test("Add counter to InsightPublisher") {
    check(genPosDouble) { v =>
      for {
        ip      <- insightPublisher
        event   <- ZIO
                     .clockWith(_.instant)
                     .map(now => New(MetricKey.counter("countMe"), MetricState.Counter(v), now))
        encoded <- InsightEncoder.encode(event)
        _       <- ip.set(encoded)
        fromIp  <- ip.getMetrics(Seq(encoded._1))
      } yield assertTrue(fromIp.states.size == 1) &&
        assertTrue(
          fromIp.states.head.state.asInstanceOf[MetricState.Counter] == encoded._2.state
            .asInstanceOf[MetricState.Counter],
        )
    }
  }

}
