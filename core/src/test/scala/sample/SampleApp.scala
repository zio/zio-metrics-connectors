package sample

import java.net.InetSocketAddress

import zio._
import zio.http._
//import zio.metrics.connectors.newrelic.NewRelicConfig
import zio.http.html._
import zio.http.model.{Headers, Method}
import zio.metrics.connectors.{prometheus, statsd, MetricsConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.statsd.StatsdConfig
import zio.metrics.jvm.DefaultJvmMetrics

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

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val serverInstall =
    Server.install(static ++ prometheusRouter)

  private lazy val runHttp = (serverInstall *> ZIO.never).forkDaemon

  private lazy val serverConfig =
    ZLayer.succeed(ServerConfig(address = new InetSocketAddress(bindPort), nThreads = nThreads))

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

      // The NewRelic reporting layer
      // NewRelicConfig.fromEnvEULayer,
      // newrelic.newRelicLayer,

      // Enable the ZIO internal metrics and the default JVM metricsConfig
      // Do NOT forget the .unit for the JVM metrics layer
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}
