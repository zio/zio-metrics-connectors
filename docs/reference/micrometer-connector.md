---
id: micrometer-connector
title: Micrometer Connector
---

The Micrometer connector forwards ZIO metrics to [Micrometer](https://micrometer.io/), a vendor-neutral metrics facade for
the JVM. Micrometer then sends the metrics to whichever monitoring system its registry supports, such as Prometheus,
Datadog, or Graphite, so you can change the backend by changing the registry and not your instrumented code.

## How It Works

The connector registers a listener with the ZIO Metrics core client. Every metric update is forwarded to the Micrometer
registry as it happens, so the registry always reflects the current state of the application and no polling interval
is involved.

## Usage

The connector needs two things from your application: a Micrometer `MeterRegistry` and a `MicrometerConfig`.

To use the connector:

1. Add the dependencies to your `build.sbt`. The first line is the connector. The second is the Prometheus registry from
   Micrometer, which is the backend in this example:

```sbt
libraryDependencies ++= Seq(
  "dev.zio"      %% "zio-metrics-connectors-micrometer" % "@VERSION@",
  "io.micrometer" % "micrometer-registry-prometheus"    % "1.17.2",
)
```

2. Choose a registry. Micrometer ships a `SimpleMeterRegistry` that keeps metrics in memory, which is useful for tests.
   This example uses `PrometheusMeterRegistry` so that the metrics can be scraped. In Micrometer 1.17.2 it lives in the
   `io.micrometer.prometheusmetrics` package.

3. Provide the layers. `micrometer.micrometerLayer` needs a `MeterRegistry` and a `MicrometerConfig` in the environment.
   `MicrometerConfig.default` sets the summary percentile precision to 3 digits. You can change it with
   `MicrometerConfig(summaryPercentileDigitsOfPrecision = 2)`.

4. Optionally enable core ZIO Metrics and the default JVM metrics. `Runtime.enableRuntimeMetrics` adds ZIO's own metrics,
   such as fiber counts and execution times. `DefaultJvmMetrics.liveV2` adds JVM metrics.

The following application records a counter and provides all four pieces:

```scala mdoc:compile-only
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.micrometer
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.metrics.jvm.DefaultJvmMetrics

import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}

object MicrometerApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("micrometer_requests", "Requests seen by Micrometer").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provide(
      ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
      ZLayer.succeed(MicrometerConfig.default),
      micrometer.micrometerLayer,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.liveV2.unit,
    )
}
```

The registry is a `PrometheusMeterRegistry`, so the application can serve its output with `PrometheusMeterRegistry#scrape`.
The [sample application](https://github.com/zio/zio-metrics-connectors/blob/series/2.x/sample-app/src/main/scala/sample/SampleMicrometerApp.scala)
serves that output on `/micrometer/prometheusMetrics`. For the general approach to serving metrics over HTTP, see
[Serving Prometheus Metrics](../how-to/serve-prometheus-metrics.md). For the Micrometer metric type mapping, see
[Metric Type Mapping](./metric-type-mapping.md).
