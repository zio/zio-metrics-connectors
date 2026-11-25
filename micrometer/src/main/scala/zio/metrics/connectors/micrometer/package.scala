package zio.metrics.connectors

import scala.collection.immutable.Iterable

import zio.{Unsafe, ZIO, ZLayer}
import zio.metrics.{MetricClient, MetricLabel}

import io.micrometer.core.instrument.{MeterRegistry, Tag}

package object micrometer {

  /**
   * Registers a listener that sends each ZIO metric update to the `MeterRegistry` as it happens, with no poll interval.
   * The listener is removed when the layer's scope closes. Requires a `MeterRegistry` and a `MicrometerConfig`, and
   * provides nothing.
   *
   * {{{
   * import io.micrometer.core.instrument.MeterRegistry
   * import io.micrometer.core.instrument.simple.SimpleMeterRegistry
   *
   * import zio._
   *
   * import zio.metrics.connectors.micrometer._
   *
   * val micrometer: ZLayer[Any, Nothing, Unit] =
   *   ZLayer.make[Unit](
   *     ZLayer.succeed[MeterRegistry](new SimpleMeterRegistry()),
   *     ZLayer.succeed(MicrometerConfig.default),
   *     micrometerLayer,
   *   )
   * }}}
   */
  lazy val micrometerLayer: ZLayer[MeterRegistry with MicrometerConfig, Nothing, Unit] =
    ZLayer.scoped(
      for {
        micrometerMetricListener <- MicrometerMetricListener.make
        _                        <- Unsafe.unsafe { unsafe =>
                                      val acquire = ZIO.succeed(MetricClient.addListener(micrometerMetricListener)(unsafe))
                                      val release = (_: Unit) => ZIO.succeed(MetricClient.removeListener(micrometerMetricListener)(unsafe))
                                      ZIO.acquireRelease(acquire)(release)
                                    }
      } yield (),
    )

  private[micrometer] def micrometerTags(zioMetricTags: Iterable[MetricLabel]): Iterable[Tag] =
    zioMetricTags.map(metricTag => Tag.of(metricTag.key, metricTag.value))
}
