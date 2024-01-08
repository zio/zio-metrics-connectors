package sample

import zio._
import zio.http._
import zio.http.template.{Dom, Html}
import zio.metrics.connectors.{MetricsConfig, prometheus, statsd}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.statsd.StatsdConfig
import zio.metrics.jvm.DefaultJvmMetrics

import java.nio.charset.StandardCharsets

/**
 * This is a sample app that shows how to use the Prometheus and StatsD connectors.
 */
object SamplePrometheusStatsDApp extends ZIOAppDefault with InstrumentedSample {

  private val bindPort = 8080

  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  private lazy val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/metrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val staticRoute: Route[Any, Nothing] =
    Method.GET / "" -> handler(Response.html(Html.fromDomElement(Dom.raw(indexPage))))

  private lazy val prometheusRoute: Route[PrometheusPublisher, Nothing] =
    Method.GET / "metrics" -> handler {
      ZIO.serviceWithZIO[PrometheusPublisher](_.get).map { response =>
        noCors(
          Response(
            status = Status.Ok,
            headers = Headers(Header.Custom(Header.ContentType.name, "text/plain; version=0.0.4")),
            body = Body.fromString(response, StandardCharsets.UTF_8)
          )
        )
      }
    }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val httpApp: HttpApp[PrometheusPublisher] =
    Routes(staticRoute, prometheusRoute).toHttpApp

  private lazy val runHttp = (Server.serve(httpApp) *> ZIO.never).forkDaemon

  private lazy val serverConfig = ZLayer.succeed(Server.Config.default.port(bindPort))

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] = (for {
    f <- runHttp
    _ <- program
    _ <- f.join
  } yield ())
    .provide(
      serverConfig,
      Server.live,

      // This is the general config for all backends
      metricsConfig,

      // The prometheus reporting layer
      prometheus.publisherLayer,
      prometheus.prometheusLayer,

      // The statsd reporting layer
      ZLayer.succeed(StatsdConfig("127.0.0.1", 8125)),
      statsd.statsdLayer,

      // Enable the ZIO internal metrics and the default JVM metricsConfig
      // Do NOT forget the .unit for the JVM metrics layer
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}
