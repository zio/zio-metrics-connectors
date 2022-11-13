package sample

import zio._
import zio.json._
import zio.metrics.connectors.{insight, prometheus, statsd, MetricsConfig}
import zio.metrics.connectors.insight.{ClientMessage, InsightPublisher}
import zio.metrics.connectors.insight.ClientMessage.encAvailableMetrics
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.statsd.StatsdConfig
import zio.metrics.jvm.DefaultJvmMetrics

//import zio.metrics.connectors.newrelic.NewRelicConfig
import zhttp.html._
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory

object SampleApp extends ZIOAppDefault with InstrumentedSample {

  private val bindPort = 8080
  private val nThreads = 5

  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  private lazy val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/prometheus/metrics">Prometheus Metrics</a></p>
      |<p><a href="/insight/keys">Insight Metrics: Get all keys</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val static =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private lazy val prometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "prometheus" / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }

  private lazy val insightAllKeysRouter =
    Http.collectZIO[Request] { case Method.GET -> !! / "insight" / "keys" =>
      ZIO.serviceWithZIO[InsightPublisher](_.getAllKeys.map(_.toJson).map(data => noCors(Response.json(data))))
    }

  // POST: /insight/metrics body Seq[MetricKey] => Seq[MetricsNotification]
  // TODO: Should we add an additional module with a layer implementation for zio-http?
  // should be added (at some point) to zio-http ...
  private lazy val insightGetMetricsRouter =
    Http.collectZIO[Request] { case req @ Method.POST -> !! / "insight" / "metrics" =>
      for {
        request  <- req.body.asString.map(_.fromJson[ClientMessage.MetricsSelection])
        response <- request match {
                      case Left(e)  =>
                        ZIO
                          .debug(s"Failed to parse the input: $e")
                          .as(
                            Response.text(e).setStatus(Status.BadRequest),
                          )
                      case Right(r) =>
                        ZIO
                          .serviceWithZIO[InsightPublisher](_.getMetrics(r.selection))
                          .map(_.toJson)
                          .map(data => noCors(Response.json(data)))
                    }
      } yield response
    }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val server =
    Server.port(bindPort) ++ Server.app(static ++ prometheusRouter ++ insightAllKeysRouter ++ insightGetMetricsRouter)

  private lazy val runHttp = (server.start *> ZIO.never).forkDaemon

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] = (for {
    f <- runHttp
    _ <- program
    _ <- f.join
  } yield ())
    .provide(
      ServerChannelFactory.auto,
      EventLoopGroup.auto(nThreads),

      // This is the general config for all backends
      metricsConfig,

      // The prometheus reporting layer
      prometheus.publisherLayer,
      prometheus.prometheusLayer,

      // The statsd reporting layer
      ZLayer.succeed(StatsdConfig("127.0.0.1", 8125)),
      statsd.statsdLayer,

      // The NewRelic reporting layer
      // NewRelicConfig.fromEnvEULayer,
      // newrelic.newRelicLayer,

      // The insight reporting layer
      insight.insightLayer,

      // Enable the ZIO internal metrics and the default JVM metricsConfig
      // Do NOT forget the .unit for the JVM metrics layer
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}
