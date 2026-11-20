package zio.metrics.connectors.newrelic

import zio._
import zio.http._
import zio.json.ast.Json
import zio.stream._

/** Posts New Relic metric JSON to the Metric API in batches. `newRelicLayer` creates one. */
trait NewRelicClient {
  private[newrelic] def send(data: Chunk[Json]): UIO[Unit]
}

/** Creates `NewRelicClient`s. */
object NewRelicClient {

  private[newrelic] def make: ZIO[NewRelicConfig & Client, Nothing, NewRelicClient] = for {
    cfg <- ZIO.service[NewRelicConfig]
    q   <- Queue.bounded[Json](cfg.maxMetricsPerRequest * 2)
    url <- ZIO.fromEither(URL.decode(cfg.newRelicURI.endpoint)).orDie
    clt  = new NewRelicClientImpl(cfg, url, q)
    _   <- clt.run
  } yield clt

  final private class NewRelicClientImpl(
    cfg: NewRelicConfig,
    uri: URL,
    publishingQueue: Queue[Json],
  )(implicit trace: Trace)
      extends NewRelicClient {

    override private[newrelic] def send(json: Chunk[Json]): UIO[Unit] =
      publishingQueue.offerAll(json).unit

    private def sendHttp(client: Client)(json: Chunk[Json]): Task[Unit] =
      ZIO.whenDiscard(json.nonEmpty) {
        val body = Json.Arr(Json.Obj("metrics" -> Json.Arr(json))).toString
        client.batched {
          Request
            .post(url = uri, body = Body.fromString(body))
            .addHeaders(headers)
        }
      }

    private[newrelic] def run: ZIO[Client, Nothing, Unit] =
      ZIO.serviceWithZIO[Client] { client =>
        ZStream
          .fromQueue(publishingQueue)
          .groupedWithin(cfg.maxMetricsPerRequest, cfg.maxPublishingDelay)
          .mapZIO(sendHttp(client))
          .runDrain
          .forkDaemon
          .unit
      }

    private lazy val headers = Headers(
      Header.Custom("Api-Key", cfg.apiKey).untyped,
      Header.ContentType(MediaType.application.json).untyped,
      Header.Accept(MediaType.any).untyped,
    )
  }
}
