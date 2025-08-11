package zio.metrics.connectors.internal

import zio._
import zio.metrics.connectors._

object MetricsClient {

  @deprecated("Use zio.metrics.connectors.internal.MetricsClient.runScoped instead")
  def make(handler: Iterable[MetricEvent] => UIO[Unit]): ZIO[MetricsConfig, Nothing, Unit] =
    runDaemon(handler)

  /**
   * Creates a [[MetricsClient]].
   * It is up to you to call [[MetricsClient.update]]/[[MetricsClient.runDaemon]].
   *
   * @param existingEventsAsNew determines whether the underlying [[MetricCache]] will begin with an empty or current state.
   *                     see [[MetricCache.makeEmpty]]/[[MetricCache.makeCurrent]] for further explanation.
   */
  def client(handler: Iterable[MetricEvent] => UIO[Unit], existingEventsAsNew: Boolean = true): UIO[MetricsClient] = {
    val makeCache: UIO[MetricCache] =
      if (existingEventsAsNew) MetricCache.makeEmpty
      else MetricCache.makeCurrent

    makeCache.map(new MetricsClient(_, handler) {})
  }

  /**
   * Creates a [[MetricsClient]] and runs it on a [[Schedule.fixed]] based on the provided [[MetricsConfig]].
   * @see [[MetricsClient.client]]
   */
  def runScoped(
    handler: Iterable[MetricEvent] => UIO[Unit],
    existingEventsAsNew: Boolean = true,
  ): ZIO[MetricsConfig & Scope, Nothing, Unit] =
    for {
      clt <- client(handler, existingEventsAsNew)
      cfg <- ZIO.service[MetricsConfig]
      _   <- clt.runScoped(cfg)
    } yield ()

  /**
   * Creates a [[MetricsClient]] and runs it on a [[Schedule.fixed]] based on the provided [[MetricsConfig]].
   * @see [[MetricsClient.client]]
   */
  def runDaemon(
    handler: Iterable[MetricEvent] => UIO[Unit],
    existingEventsAsNew: Boolean = true,
  ): ZIO[MetricsConfig, Nothing, Unit] =
    for {
      clt <- client(handler, existingEventsAsNew)
      cfg <- ZIO.service[MetricsConfig]
      _   <- clt.runDaemon(cfg)
    } yield ()

}

/**
 * A lightweight wrapper around a [[MetricCache]],
 * where events emitted from state diffs are passed to the provided handler.
 *
 * [[MetricsClient]] constructors deliberately do not allow passing a [[MetricCache]],
 * as this would allow a holder of the cache to call [[MetricCache.update]],
 * therefore making the client wrapper receive an inaccurate history of events.
 */
sealed abstract class MetricsClient(
  cache: MetricCache,
  handler: Iterable[MetricEvent] => UIO[Unit]) {

  /**
   * Compare the existing and current metric states,
   * and call the provided [[handler]] with the events resulting from the comparison of those two states.
   */
  def update(implicit trace: Trace): UIO[Unit] =
    cache.update.flatMap(handler)

  private def run(metricsCfg: MetricsConfig)(implicit trace: Trace): UIO[Any] =
    update.schedule(Schedule.duration(10.millis) ++ Schedule.fixed(metricsCfg.interval))

  def runScoped(metricsCfg: MetricsConfig)(implicit trace: Trace): URIO[Scope, Unit] =
    run(metricsCfg).forkScoped.unit

  def runDaemon(metricsCfg: MetricsConfig)(implicit trace: Trace): UIO[Unit] =
    run(metricsCfg).forkDaemon.unit

}
