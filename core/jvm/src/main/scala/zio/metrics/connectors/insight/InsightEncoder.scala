package zio.metrics.connectors.insight

import java.time.Instant

import zio.ZIO
import zio.metrics.MetricKey
import zio.metrics.MetricState
import zio.metrics.connectors.MetricEvent

case object InsightEncoder {
  def encode(event: MetricEvent): ZIO[Any, Nothing, (MetricKey[Any], MetricState[Any])] =
    ZIO.succeed(encodeMetric(event.metricKey, event.current, event.timestamp))

  private def encodeMetric(
    key: MetricKey.Untyped,
    state: MetricState.Untyped,
    timestamp: Instant,
  ): (MetricKey[Any], MetricState[Any]) = (key, state)
}
