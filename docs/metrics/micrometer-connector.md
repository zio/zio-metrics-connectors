---
id: micrometer-connector
title: Micrometer Connector
---

ZIO Metrics has an integration with [Micrometer](https://micrometer.io/), a powerful metrics instrumentation library.
By combining these two frameworks, developers can benefit from comprehensive and flexible metrics monitoring capabilities
within their ZIO applications. This integration allows for efficient monitoring, gathering, and reporting of key
performance indicators (KPIs) and other metrics for enhanced observability.

## Benefits

Micrometer integration offers a range of benefits that make it an excellent choice for integrating with ZIO Metrics.
Here are some key advantages:

### 1. Comprehensive Metrics Support

Micrometer provides a vendor-neutral facade for various monitoring systems, including Prometheus, Datadog, Graphite,
and more. The same like slf4j for logs but for observability. It offers a unified API for recording metrics, allowing
developers to easily integrate with multiple backend systems and switch between different monitoring systems without
major code changes for its ZIO applications. You can choose whatever you want as monitoring tool - Grafana,
Prometheus, and Jaeger and so on.

### 2. Real-time Metrics Updates

By leveraging the low-level integration with the ZIO Metrics core client listener, the module provides immediate
updates to Micrometer whenever metric changes occur within the ZIO application. This real-time synchronization
ensures that the metrics reported by Micrometer accurately reflect the current state of the application, enabling
timely monitoring and analysis.

### 3. Always on trend of standards

Integration with Micrometer ensures that developers are always on trend of standards with metrics format changes and
can take advantage of the latest updates and improvements in the monitoring ecosystem. By relying on Micrometer as
the integration layer, developers gain compatibility with the latest monitoring systems, future-proof their
monitoring infrastructure, benefit from community-driven updates and support, and simplify maintenance and upgrades.
This integration provides a reliable and scalable solution for metrics monitoring in ZIO-based applications, allowing
developers to focus on their core business logic while staying at the forefront of monitoring technology.

## Example of usage

Unlike the Prometheus and StatsD connectors, the Micrometer connector does not poll on a `MetricsConfig` interval.
It registers a listener with ZIO Metrics, so every change is forwarded to the Micrometer registry as it happens. The
connector needs two things from your application: a Micrometer `MeterRegistry` and a `MicrometerConfig`.

To use the connector:

1. Add the dependencies to your `build.sbt`. The first line is the connector. The second is the Prometheus registry from
   Micrometer, which is the backend in this example:

```scala
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
   such as fiber counts and execution times. `DefaultJvmMetrics.live` adds JVM metrics.

The following application records a counter and provides all four pieces:

```scala
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
      DefaultJvmMetrics.live.unit,
    )
}
```

The registry is a `PrometheusMeterRegistry`, so the application can serve its output with `PrometheusMeterRegistry#scrape`.
The [sample application](https://github.com/zio/zio-metrics-connectors/blob/series/2.x/sample-app/src/main/scala/sample/SampleMicrometerApp.scala)
serves that output on `/micrometer/prometheusMetrics`. For the general approach to serving metrics over HTTP, see
[Serving Prometheus Metrics](../how-to/serve-prometheus-metrics.md). For the Micrometer metric type mapping, see
[Metric Type Mapping](../reference/metric-type-mapping.md).
