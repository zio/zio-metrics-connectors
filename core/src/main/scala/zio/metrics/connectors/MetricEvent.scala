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

sealed trait MetricEvent {
  def metricKey: MetricKey.Untyped
  def current: MetricState.Untyped
  def timestamp: Instant
}

object MetricEvent {

  final case class New private[connectors] (
    override val metricKey: MetricKey.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  final case class Unchanged private[connectors] (
    override val metricKey: MetricKey.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  final case class Updated private[connectors] (
    override val metricKey: MetricKey.Untyped,
    oldState: MetricState.Untyped,
    override val current: MetricState.Untyped,
    override val timestamp: Instant)
      extends MetricEvent

  // Kept for backward compatibility
  @deprecated("Use the other MetricEvent.make instead", "2.4.2")
  def make[Type <: MetricKeyType { type Out = Out0 }, Out0](
    metricKey: MetricKey[Type],
    oldState: Option[MetricState[Out0]],
    newState: MetricState[Out0],
  ): Either[IllegalArgumentException, MetricEvent] =
    make(metricKey, oldState, newState, Instant.now())

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
