package zio.metrics.connectors.newrelic

import zio._
import zio.http._
import zio.http.model.{Header, Headers}
import zio.json.ast.Json
import zio.stream._

trait NewRelicClient {
  private[newrelic] def send(data: Chunk[Json]): UIO[Unit]
}

object NewRelicClient {

  private[newrelic] def make: ZIO[NewRelicConfig & Client, Nothing, NewRelicClient] = for {
    cfg <- ZIO.service[NewRelicConfig]
    q   <- Queue.bounded[Json](cfg.maxMetricsPerRequest * 2)
    clt  = new NewRelicClientImpl(cfg, q) {}
    _   <- clt.run
  } yield clt

  sealed abstract private class NewRelicClientImpl(
    cfg: NewRelicConfig,
    publishingQueue: Queue[Json],
  )(implicit trace: Trace)
      extends NewRelicClient {

    override private[newrelic] def send(json: Chunk[Json]) =
      publishingQueue.offerAll(json).unit

    private def sendHttp(json: Chunk[Json]) =
      ZIO
        .fromEither {
          val body = Json
            .Arr(
              Json.Obj("metrics" -> Json.Arr(json.toSeq: _*)),
            )
            .toString

          URL.fromString(cfg.newRelicURI.endpoint).map { url =>
            Request
              .post(
                url = url,
                body = Body.fromString(body),
              )
              .addHeaders(headers)
          }
        }
        .flatMap(request => Client.request(request).unit)
        .when(json.nonEmpty)

    private[newrelic] def run =
      ZStream
        .fromQueue(publishingQueue)
        .groupedWithin(cfg.maxMetricsPerRequest, cfg.maxPublishingDelay)
        .mapZIO(sendHttp)
        .runDrain
        .forkDaemon
        .unit

    private lazy val headers = Headers(
      Header("Api-Key", cfg.apiKey),
      Header("Content-Type", "application/json"),
      Header("Accept", "*/*"),
    )
  }
}
