package sample

import zio._
import zio.http._
import zio.metrics.connectors.micrometer
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.metrics.jvm.DefaultJvmMetrics
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import zio.http.template.{Dom, Html}

/**
 * This is a sample app that shows how to use the Micrometer connector.
 */
object SampleMicrometerApp extends ZIOAppDefault with InstrumentedSample {

  private val bindPort = 8080

  private lazy val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/micrometer/prometheusMetrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val staticRoute =
    Method.GET / "" -> handler(Response.html(Html.fromDomElement(Dom.raw(indexPage))))

  private lazy val micrometerPrometheusRouter =
    Method.GET / "micrometer" / "prometheusMetrics" -> handler {
      ZIO.serviceWith[PrometheusMeterRegistry](m => noCors(Response.text(m.scrape())))
    }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val httpApp: HttpApp[PrometheusMeterRegistry] =
    Routes(staticRoute, micrometerPrometheusRouter).toHttpApp

  private lazy val runHttp = (Server.serve(httpApp) *> ZIO.never).forkDaemon

  private lazy val serverConfig =
    ZLayer.succeed(Server.Config.default.port(bindPort))

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] = (for {
    f <- runHttp
    _ <- program
    _ <- f.join
  } yield ())
    .provide(
      serverConfig,
      Server.live,
      ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
      ZLayer.succeed(MicrometerConfig.default),
      micrometer.micrometerLayer,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}
