---
id: index
title: "Introduction to ZIO Metrics Connectors"
sidebar_label: "Introduction"
---

ZIO Metrics Connectors provides integration between ZIO metrics and industry-leading monitoring and observability systems such as
  * [Prometheus](https://prometheus.io/)
  * [Datadog](https://docs.datadoghq.com/)
  * [New Relic](https://newrelic.com)
  * [StatsD](https://github.com/statsd/statsd)
  * [Micrometer](https://micrometer.io)

@PROJECT_BADGES@

## Installation

Add the core library to your `build.sbt`. All modules are published for Scala 2.12, Scala 2.13 and Scala 3, and run on the JVM only:

```sbt
libraryDependencies ++= Seq(
  // Core library: connector settings and the shared metrics client (JVM)
  "dev.zio" %% "zio-metrics-connectors" % "@VERSION@"
)
```

Then add the connector for your monitoring backend, as described in [Modules](#modules).

## Modules

Each connector is a separate module. Add only the ones that you use:

```sbt
libraryDependencies ++= Seq(
  // Prometheus: encodes metrics as Prometheus text for you to serve over HTTP (JVM)
  "dev.zio" %% "zio-metrics-connectors-prometheus" % "@VERSION@",

  // StatsD: sends datagrams over UDP or a Unix domain socket (JVM)
  "dev.zio" %% "zio-metrics-connectors-statsd" % "@VERSION@",

  // Datadog: sends DogStatsD datagrams through a StatsD client, also adds `zio-metrics-connectors-statsd` (JVM)
  "dev.zio" %% "zio-metrics-connectors-datadog" % "@VERSION@",

  // New Relic: sends metrics to the New Relic Metric API over HTTPS, also adds `zio-http` and `zio-json` (JVM)
  "dev.zio" %% "zio-metrics-connectors-newrelic" % "@VERSION@",

  // Micrometer: forwards every metric update to a Micrometer `MeterRegistry` (JVM)
  "dev.zio" %% "zio-metrics-connectors-micrometer" % "@VERSION@"
)
```

Each module depends on the core library, so you do not need to list it separately. Learn more about each module in its
reference page: [Prometheus](reference/prometheus-client.md), [StatsD](reference/statsd-client.md),
[Datadog](reference/datadog-client.md), [New Relic](reference/new-relic-client.md), and
[Micrometer](reference/micrometer-connector.md).

To pick a connector, see [How to Choose a Connector](how-to/choose-a-connector.md). To instrument an application and see
its metrics in Prometheus, follow the [tutorial](tutorial/instrument-and-scrape.md).
