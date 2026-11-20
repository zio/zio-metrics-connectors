package zio.metrics.connectors.prometheus

import zio._

/**
 * Holds the latest Prometheus text. `prometheusLayer` replaces the text on each poll. The application serves it, for
 * example from an HTTP route, by calling `get`.
 *
 * {{{
 * import zio._
 *
 * import zio.metrics.connectors.prometheus._
 *
 * val scrape: URIO[PrometheusPublisher, String] = ZIO.serviceWithZIO[PrometheusPublisher](_.get)
 * }}}
 */
trait PrometheusPublisher {

  /** Returns the latest text. It is empty until the first poll. */
  def get(implicit trace: Trace): UIO[String]

  /** Replaces the text. The poll loop calls this, and applications normally do not. */
  def set(next: String)(implicit trace: Trace): UIO[Unit]
}

/** The `PrometheusPublisher` that keeps its text in a `Ref`. */
final class PrometheusPublisherLive private[prometheus] (current: Ref[String]) extends PrometheusPublisher {

  /** Returns the text held in the `Ref`. */
  def get(implicit trace: Trace): UIO[String] = current.get

  /** Stores the text in the `Ref`. */
  def set(next: String)(implicit trace: Trace): UIO[Unit] = current.set(next)
}

/** Creates `PrometheusPublisher`s. */
object PrometheusPublisher {

  /** Creates a publisher whose text is empty. */
  def make: UIO[PrometheusPublisher] =
    for {
      current <- Ref.make[String]("")
    } yield new PrometheusPublisherLive(current)

}
