package zio.metrics.connectors.insight

import java.time.Instant
import java.util.UUID

import zio.ZIO
import zio.metrics.{MetricKey, MetricState}
import zio.metrics.connectors.MetricEvent
import zio.metrics.connectors.insight.ClientMessage.InsightMetricState

private[connectors] case object InsightEncoder {
  def encode(event: MetricEvent): ZIO[Any, Nothing, (UUID, InsightMetricState)] =
    ZIO.succeed(encodeMetric(event.metricKey, event.current, event.timestamp))

  private def encodeMetric(
    key: MetricKey.Untyped,
    state: MetricState.Untyped,
    timestamp: Instant,
  ): (UUID, InsightMetricState) = {
    val id = java.util.UUID.nameUUIDFromBytes(key.toString.getBytes)
    (
      id,
      InsightMetricState(
        id = id,
        key = key,
        state = state,
        timestamp = timestamp,
      ),
    )
  }

}
