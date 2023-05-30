package zio.metrics.connectors

import scala.collection.immutable.Iterable

import zio.{Unsafe, ZIO, ZLayer}
import zio.metrics.{MetricClient, MetricLabel}

import io.micrometer.core.instrument.{MeterRegistry, Tag}

package object micrometer {

  lazy val micrometerLayer: ZLayer[MeterRegistry, Nothing, Unit] =
    ZLayer.scoped(
      for {
        micrometerMetricListener <- MicrometerMetricListener.make
        _                        <- Unsafe.unsafe(implicit unsafe =>
                                      ZIO.acquireRelease(ZIO.succeed(MetricClient.addListener(micrometerMetricListener)))(_ =>
                                        ZIO.succeed(MetricClient.removeListener(micrometerMetricListener)),
                                      ),
                                    )
      } yield (),
    )

  private[micrometer] def micrometerTags(zioMetricTags: Iterable[MetricLabel]): Iterable[Tag] =
    zioMetricTags.map(metricTag => Tag.of(metricTag.key, metricTag.value))

}
