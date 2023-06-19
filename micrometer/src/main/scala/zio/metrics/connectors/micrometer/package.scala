package zio.metrics.connectors

import scala.collection.immutable.Iterable

import zio.{Unsafe, ZIO, ZLayer}
import zio.metrics.{MetricClient, MetricLabel}

import io.micrometer.core.instrument.{MeterRegistry, Tag}

package object micrometer {

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
