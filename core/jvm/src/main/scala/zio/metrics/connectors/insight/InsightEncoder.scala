package zio.metrics.connectors.insight

import zio.metrics.connectors.MetricEvent
import zio.ZIO
import zio.metrics.MetricKey

case object InsightEncoder {
  def encode(event: MetricEvent): ZIO[Any, Throwable, MetricKey[Any]] = ???
}
