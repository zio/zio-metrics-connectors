package sample

import zio._
import zio.http._
import zio.http.html._
import zio.metrics.connectors.{prometheus, statsd, MetricsConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.statsd.StatsdConfig
import zio.metrics.jvm.DefaultJvmMetrics

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
      |<p><a href="/prometheus/metrics">Prometheus Metrics</a></p>
      |<p><a href="/insight/keys">Insight Metrics: Get all keys</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val static =
    Http.collect[Request] { case Method.GET -> Root => Response.html(Html.fromString(indexPage)) }

  private lazy val prometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> Root / "prometheus" / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val serverInstall =
    Server.install(static ++ prometheusRouter)

  private lazy val runHttp = (serverInstall *> ZIO.never).forkDaemon

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
