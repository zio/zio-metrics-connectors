package zio.metrics.connectors

import zio._
import zio.metrics.connectors.internal.MetricsClient

package object insight {

  lazy val publisherLayer: ULayer[InsightPublisher] = ZLayer.fromZIO(InsightPublisher.make)

  lazy val insightLayer: ZLayer[MetricsConfig & InsightPublisher, Nothing, Unit] =
    ZLayer.fromZIO(
      ZIO.service[InsightPublisher].flatMap(clt => MetricsClient.make(insightHandler(clt))).unit,
    )

  private def insightHandler(clt: InsightPublisher): Iterable[MetricEvent] => UIO[Unit] = events => {

    val evtFilter: MetricEvent => Boolean = {
      case MetricEvent.Unchanged(_, _, _) => false
      case _                              => true
    }

    val update =
      ZIO
        .foreach(events.filter(evtFilter))(evt =>
          for {
            encoded <- InsightEncoder.encode(evt)
            _       <- clt.set(encoded)
          } yield (),
        )
        .unit

    update
  }

}
