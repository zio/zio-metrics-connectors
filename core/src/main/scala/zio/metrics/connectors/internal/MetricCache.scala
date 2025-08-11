package zio.metrics.connectors.internal

import zio._
import zio.internal.metrics.metricRegistry
import zio.metrics._
import zio.metrics.connectors._

sealed abstract class MetricCache(latestSnapshot: Ref[Set[MetricPair.Untyped]]) {

  /**
   * Retrieve the cached metric state.
   */
  def snapshot(implicit trace: Trace): UIO[Set[MetricPair.Untyped]] = latestSnapshot.get

  /**
   * Update the cached metric state, and return a set of diffs compared to the existing cache.
   */
  def update(implicit trace: Trace): UIO[Set[MetricEvent]] =
    latestSnapshot.modify { old =>
      // first we get the state for all metrics that we had captured in the last run
      val oldMap = stateMap(old)
      // then we get the snapshot from the underlying metricRegistry
      val next   = Unsafe.unsafe(implicit u => metricRegistry.snapshot())
      val res    = events(oldMap, next)
      (res, next)
    }

  // This will create a map for the metrics captured in the last snapshot
  private def stateMap(metrics: Set[MetricPair.Untyped]): Map[MetricKey.Untyped, MetricState.Untyped] = {

    val builder = scala.collection.mutable.Map[MetricKey.Untyped, MetricState.Untyped]()
    val it      = metrics.iterator
    while (it.hasNext) {
      val e = it.next()
      builder.update(e.metricKey, e.metricState)
    }

    builder.toMap
  }

  private def events(
    oldState: Map[MetricKey.Untyped, MetricState.Untyped],
    metrics: Set[MetricPair.Untyped],
  ): Set[MetricEvent] =
    metrics
      .map { mp =>
        MetricEvent.make(mp.metricKey, oldState.get(mp.metricKey), mp.metricState)
      }
      .collect { case Right(e) => e }

}
object MetricCache {

  private def make(current: => Set[MetricPair.Untyped]): UIO[MetricCache] =
    Ref.make(current).map(new MetricCache(_) {})

  /**
   * Creates a new [[MetricCache]] with an initial empty cache.
   * If the metric registry contains existing events at the time this state is created,
   * the first evaluation of [[MetricCache.update]] will emit [[MetricEvent.New]] for these events.
   */
  val makeEmpty: UIO[MetricCache] = make(Set.empty)

  /**
   * Creates a new [[MetricCache]] with an initial cache of the current metricRegistry state.
   * If the metric registry contains existing events at the time this state is created,
   * the first evaluation of [[MetricCache.update]] will emit [[MetricEvent.Unchanged]] for these events.
   */
  val makeCurrent: UIO[MetricCache] = make(Unsafe.unsafe(implicit u => metricRegistry.snapshot()))

}
