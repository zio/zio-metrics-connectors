---
id: index
title: "Metrics"
---
ZIO Metrics enables the instrumentation of any ZIO based application with specialized aspects. The type of the original ZIO effect will not change by adding on or more aspects to it. 

Whenever an instrumented effect executes, all the aspects will be executed as well and each of the 
aspects will capture some data of interest and update some Metric internal state. Which data will be captured and how it can be used later on is dependent on the metric type associated with the aspect. 

Metrics are normally captured to be displayed in an application like [Grafana](https://grafana.com/) or a cloud based platform like [DatadogHQ](https://docs.datadoghq.com/) 
or [NewRelic](https://newrelic.com). 
In order to support such a range of different platforms, the metric state is kept in an internal data structure optimized to update the state as efficiently as possible 
and the data required by one or more of the platforms is generated only when it is required. 

ZIO Metrics Connectors also allows us to dump current metric states (in one of the next minor releases) out of the box to analyze the metrics in the development phase before the decision 
for a metric platform has been made or in cases when the platform might not be feasible to use in development. 

> Changing the targeted reporting back end will have no impact on the application at all. Once instrumented properly, that reporting back end decision will happen __at the end of the world__
> in the ZIO applications mainline by injecting one or more of the available reporting clients.

Currently, ZIO Metrics Connectors provides integrations with the following backends:
* [Prometheus](https://prometheus.io/)
* [Datadog](https://docs.datadoghq.com/)
* [New Relic](https://newrelic.com)
* [StatsD](https://github.com/statsd/statsd)
* [Micrometer](https://micrometer.io)

### Adding ZIO Metrics Connectors to your project

Import the corresponding dependency based on your reporting infrastructure:
```sbt
libraryDependencies ++= {
  Seq(
    "dev.zio" %% "zio-metrics-connectors"             % "@VERSION@", // core library
    "dev.zio" %% "zio-metrics-connectors-prometheus"  % "@VERSION@", // Prometheus client
    "dev.zio" %% "zio-metrics-connectors-datadog"     % "@VERSION@", // DataDog client
    "dev.zio" %% "zio-metrics-connectors-newrelic"    % "@VERSION@", // NewRelic client
    "dev.zio" %% "zio-metrics-connectors-statsd"      % "@VERSION@", // StatsD client
    "dev.zio" %% "zio-metrics-connectors-micrometer"  % "@VERSION@"  // Micrometer client
  )
}
```

## General Metric architecture

All metrics have a name of type `String` which may be augmented by zero or many `Label`s. A `Label` is simply a key/value pair to further qualify the name. 
The distinction is made, because some reporting platforms support tags as well and provide certain aggregation mechanisms for them. 

> For example, think of a counter that simply counts how often a particular service has been invoked. If the application 
> is deployed across several hosts, we might model our counter with a name `myService`and an additional label 
> `(host, ${hostname})`. With such a definition we would see the number of executions for each host, but we could also 
> create a query in Grafana or DatadogHQ to visualize the aggregated value over all hosts. Using more than one label 
> would allow to create visualizations across any combination of the labels. 

An important aspect of metric aspects is that they _understand_ values of a certain type. For example, a Gauge 
understands `Double` values to manipulate the current value within the gauge. This implies, that for effects 
`ZIO[R, E, A]` where `A` can not be assigned to a `Double` we have to provide a mapping function `A => Double` so that 
we can derive the measured value from the effect´s result. 

Finally, more complex metrics might require additional information to specify them completely. For example, within a 
[histogram](metric-reference.md#histograms) we need to specify the buckets the observed values shall be counted in. 

Please refer to 

* [Metrics Reference](metric-reference.md) for more information on the metrics currently supported
* [Prometheus Client](prometheus-client.md) to learn more about the mapping from ZIO Metrics to Prometheus
* [StatsD Client](statsd-client.md) to learn more about the mapping from ZIO Metrics to StatsD
* [DataDog Client](datadog-client.md) to learn more about the mapping from ZIO Metrics to DataDog
* [Micrometer](micrometer-connector.md) to learn more about the mapping from ZIO Metrics to Micrometer
