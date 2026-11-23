/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.metrics.connectors

import java.time.Instant

import zio.metrics._

/**
 * The state of one metric at one poll of the metrics registry, compared with the previous poll. Polling connectors receive
 * a set of these for each poll.
 *
 * The three kinds are [[MetricEvent.New]], [[MetricEvent.Updated]] and [[MetricEvent.Unchanged]].
 */
sealed trait MetricEvent {

  /** The key of the metric: its name, tags and description. */
  def metricKey: MetricKey.Untyped

  /** The state of the metric at this poll. */
  def current: MetricState.Untyped

  /** The time of the poll that produced this event. */
  def timestamp: Instant
}

/** Builds [[MetricEvent]]s from the state of a metric at the previous poll and at this poll. */
object MetricEvent {

  /** A metric seen for the first time, so it has no earlier state. */
  final case class New private[connectors] (
    override val metricKey: MetricKey.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  /** A metric whose state is equal to its state at the previous poll. */
  final case class Unchanged private[connectors] (
    override val metricKey: MetricKey.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  /**
   * A metric whose state differs from its state at the previous poll.
   *
   * `oldState` is the state at the previous poll and `current` is the state at this poll.
   */
  final case class Updated private[connectors] (
    override val metricKey: MetricKey.Untyped,
    oldState: MetricState.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  // Kept for backward compatibility
  /**
   * Builds the event like the four-argument `make`, with the current time as the poll time.
   *
   * Deprecated: use the four-argument `make`, which takes the poll time.
   */
  @deprecated("Use the other MetricEvent.make instead", "2.4.2")
  def make[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
  ): Either[IllegalArgumentException, MetricEvent] =
    make(metricKey, oldState, newState, Instant.now())

  /**
   * Builds the event for one metric, from its state at the previous poll and its state now.
   *
   * The result is:
   *   - `New` when `oldState` is `None`;
   *   - `Updated` when the two states differ;
   *   - `Unchanged` when they are equal.
   *
   * Counters, gauges and frequencies are compared by value. Summaries and histograms are compared by their count only,
   * so a change in `sum` alone gives `Unchanged`. A gauge holding `NaN` gives `Updated` on every poll, because `NaN` is
   * not equal to itself: a known bug.
   *
   * Returns `Left` with an `IllegalArgumentException` when the two states are of different kinds, such as a counter and
   * a gauge. The message names both states.
   *
   * @param metricKey
   *  The key of the metric
   * @param oldState
   *  The state at the previous poll, or `None` when the metric is new
   * @param newState
   *  The state at this poll
   * @param now
   *  The time of this poll, stored in the event
   */
  def make[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
    now: Instant,
  ): Either[IllegalArgumentException, MetricEvent] = {
    val event = unsafeMake(metricKey, oldState, newState, now)

    if (event ne null) Right(event)
    else Left(new IllegalArgumentException(s"Unsupported MetricState combination: ${oldState.get}, $newState"))
  }

  private[zio] def unsafeMake[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
    now: Instant,
  ): MetricEvent =
    oldState match {
      case None            => New(metricKey, newState, now)
      case Some(oldState0) =>
        @inline def updated   = Updated(metricKey, oldState0, newState, now)
        @inline def unchanged = Unchanged(metricKey, newState, now)

        oldState0 match {
          case MetricState.Counter(oldCount) =>
            newState match {
              case MetricState.Counter(newCount) => if (oldCount != newCount) updated else unchanged
              case _                             => null
            }

          case MetricState.Gauge(oldValue) =>
            newState match {
              case MetricState.Gauge(newValue) => if (oldValue != newValue) updated else unchanged
              case _                           => null
            }

          case MetricState.Frequency(oldOccurences) =>
            newState match {
              case MetricState.Frequency(newOccurrences) => if (oldOccurences != newOccurrences) updated else unchanged
              case _                                     => null
            }

          case MetricState.Summary(_, _, oldCount, _, _, _) =>
            newState match {
              case MetricState.Summary(_, _, newCount, _, _, _) => if (oldCount != newCount) updated else unchanged
              case _                                            => null
            }

          case MetricState.Histogram(_, oldCount, _, _, _) =>
            newState match {
              case MetricState.Histogram(_, newCount, _, _, _) => if (oldCount != newCount) updated else unchanged
              case _                                           => null
            }
        }
    }

}
