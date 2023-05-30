package sample

import java.net.InetSocketAddress

import zio._
import zio.http._
import zio.http.html._
import zio.http.model.{Headers, Method}
import zio.metrics.connectors.micrometer
import zio.metrics.jvm.DefaultJvmMetrics
import zio.sample.InstrumentedSample

import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}

object SampleApp extends ZIOAppDefault with InstrumentedSample {

  private val bindPort = 8080
  private val nThreads = 5

  private lazy val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/micrometer/prometheusMetrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val static =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private lazy val micrometerPrometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "micrometer" / "prometheusMetrics" =>
        ZIO.serviceWith[PrometheusMeterRegistry](m => noCors(Response.text(m.scrape())))
      }

  private def noCors(r: Response): Response =
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))

  private val serverInstall =
    Server.install(static ++ micrometerPrometheusRouter)

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
      ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
      micrometer.micrometerLayer,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}
