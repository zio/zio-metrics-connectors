package zio.metrics.connectors.prometheus

import zio._

trait PrometheusPublisher {
  def get(implicit trace: Trace): UIO[String]
  def set(next: String)(implicit trace: Trace): UIO[Unit]
}

final class PrometheusPublisherLive private[prometheus] (current: Ref[String]) extends PrometheusPublisher {
  def get(implicit trace: Trace): UIO[String]             = current.get
  def set(next: String)(implicit trace: Trace): UIO[Unit] = current.set(next)
}

object PrometheusPublisher {

  def make: UIO[PrometheusPublisher] =
    for {
      current <- Ref.make[String]("")
    } yield new PrometheusPublisherLive(current)

}
