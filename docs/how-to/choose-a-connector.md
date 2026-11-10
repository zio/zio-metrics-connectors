---
id: choose-a-connector
title: "How to Choose a Connector"
sidebar_label: "Choose a Connector"
---

Your instrumented code does not change when you change the monitoring backend. You only decide which connector to provide
when the application starts. This guide explains how the connectors differ, so that you can pick the one your monitoring
setup needs.

The connectors differ in two ways. The first is direction: a pull connector waits for a backend to request the data,
while a push connector sends the data to the backend. The second is what your application must provide: some connectors
need an HTTP endpoint, others need a network address or an API key.

| Connector   | Direction | How Data Leaves the App                       | Layers You Provide                                              |
| ----------- | --------- | --------------------------------------------- | --------------------------------------------------------------- |
| Prometheus  | Pull      | Your HTTP route returns the publisher text    | `publisherLayer`, `prometheusLayer`, `MetricsConfig`            |
| StatsD      | Push      | UDP datagrams, or a Unix domain socket        | `statsdUDP` or `statsdUDS`, `StatsdConfig` or `DatagramSocketConfig`, `MetricsConfig` |
| Datadog     | Push      | DogStatsD datagrams to a Datadog agent        | A StatsD client layer, `datadog.live`, `DatadogPublisherConfig`, `MetricsConfig` |
| New Relic   | Push      | HTTPS requests to the New Relic Metric API    | `newRelicLayer`, `NewRelicConfig`, `MetricsConfig`              |
| Micrometer  | In-process | Listener that updates a Micrometer registry  | `micrometerLayer`, a `MeterRegistry`, `MicrometerConfig`        |

Use the following steps to pick a connector:

1. If you run a Prometheus server, use the Prometheus connector. Prometheus scrapes the endpoint that you serve, so
   the application needs no address for the server.
2. If your hosts run a Datadog agent, use the Datadog connector. If you run a different StatsD server, use the StatsD
   connector instead.
3. If you send metrics to New Relic, use the New Relic connector. It needs an API key and reports over HTTPS.
4. If your application already uses a Micrometer registry, use the Micrometer connector. Micrometer then forwards the
   metrics to whichever backend that registry supports.

Each connector has its own page with the details: [Prometheus](../metrics/prometheus-client.md),
[StatsD](../metrics/statsd-client.md), [Datadog](../metrics/datadog-client.md),
[New Relic](../metrics/new-relic-client.md), and [Micrometer](../metrics/micrometer-connector.md).

Avoid providing two connectors that send to the same backend, because each metric is then reported twice. Read the
[Datadog page](../metrics/datadog-client.md) before you combine the Datadog and StatsD layers.
