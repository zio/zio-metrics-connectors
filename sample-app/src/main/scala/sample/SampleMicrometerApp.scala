package sample

import java.net.InetSocketAddress

import zio._
import zio.http._
import zio.http.html._
import zio.metrics.connectors.micrometer
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.metrics.jvm.DefaultJvmMetrics

import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}

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

  private lazy val static =
    Http.collect[Request] { case Method.GET -> Root => Response.html(Html.fromString(indexPage)) }

  private lazy val micrometerPrometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> Root / "micrometer" / "prometheusMetrics" =>
        ZIO.serviceWith[PrometheusMeterRegistry](m => noCors(Response.text(m.scrape())))
      }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val serverInstall =
    Server.install(static ++ micrometerPrometheusRouter)

  private lazy val runHttp = (serverInstall *> ZIO.never).forkDaemon

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
