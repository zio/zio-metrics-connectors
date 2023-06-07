---
id: zmx-metric-reference
title: "ZMX Metric Reference"
---

```scala mdoc:invisible
import zio._
import zio.metrics._
```

All metrics in ZMX are defined in the form of aspects that can be applied to effects without changing
the signature of the effect it is applied to.

Also, every `Metric`s implementation (kind of metric) are further qualified by a type parameter `In` that must be
compatible with the output type of the effect. Practically this means that, for example, a `Metric.Counter[Any]` can be applied
to any effect while a `Metric.Counter[Double]` can only be applied to effects producing a `Double`.

Finally, each metric understands a certain data type it can observe to manipulate its state.
Counters, Gauges, Histograms and Summaries all understand `Double` values while a Frequency understands
`String` values.

In cases where the output type of effect is not compatible with the type required to manipulate the
metric, the API defines a `contramap` method to construct a `Metric[_, In2, _]` with a mapper function
from `In` to the type required by the metric.

There is also an ability to setting up additional conditions for metric value capture.
Such methods like `trackAll`, `trackDefectWith`, `trackDurationWith`, `trackErrorWith` and `trackSuccessWith` allow for
customized tracking based on specific criteria. This flexibility enables us to define our own tracking logic and metrics
based on the requirements of our application. For example, we can track defects only when certain conditions are met or
track the duration of specific ZIO effects.
The ZIO Metric methods like `trackErrorWith` allow capturing and tracking
errors in ZIO effects.
Each of this help methods returns new `ZIOAspect`, for example:

```scala
val countAllErrors: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] = Metric.counter("countAllErrors").contramap[Any](_ => 1L).trackError
```

It is possible to add some custom tag to Metric via `tagged()` methods.

```scala
val countRequests = Metric.counter("countRequests")

val countRequestsByPath = for {
  _ <- requestLogic @@ countRequests.tagged("path", path)
} yield ()
```

The API functions in this document are implemented in the `Metric` object. An aspect can be applied to
an effect with the `@@` operator.

Once an application is instrumented with ZMX aspects, it can be configured with a client implementation
that is responsible for providing the captured metrics to an appropriate backend. Currently, ZMX supports
clients for [StatsD](statsd-client.md) and [Prometheus](prometheus-client.md) out of the box.

## Counter

A counter in ZMX is simply a named variable that increases over time.

### API

Create a counter which is incremented by value produced by effect every time it is executed successfully. This can be
applied to any effect.

```scala
def counter(name: String): Metric.Counter[Long]

def counterDouble(name: String): Metric.Counter[Double]

def counterInt(name: String): Metric.Counter[Int]
```

### Examples

Create a counter named `countAll` which is incremented by `1` every time it is invoked.

```scala 
val aspCountAll = Metric.counter("countAll").contramap[Any](_ => 1L)
```

After contramap to Any, the counter can be applied to any effect. Note, that the same aspect can be applied
to more than one effect. In the example we would count the sum of executions of both effects
in the for comprehension.

```scala 
val countAll = for {
  _ <- ZIO.unit @@ aspCountAll
  _ <- ZIO.unit @@ aspCountAll
} yield ()
```  

Create a counter named `countBytes` that can be applied to effects having the output type `Double`.

```scala
val aspCountBytes = Metric.counterDouble("countBytes")
```

Now we can apply it to effects producing `Double` (in a real application the value might be
the number of bytes read from a stream or something similar):

```scala 
val countBytes = nextDoubleBetween(0.0d, 100.0d) @@ aspCountBytes
```

## Gauges

A gauge in ZMX is a named variable of type `Double` that can change over time. It can either be set
to an absolute value or relative to the current value.

### API

Create a gauge that can be set to absolute values. It can be applied to effects yielding a Double

```scala
def gauge(name: String): Metric.Gauge[Double]
```

### Examples

Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double

```scala
val aspGauge = Metric.gauge("setGauge")
```

Now we can apply these effects to effects having an output type `Double`. Note that we can instrument
an effect with any number of aspects if the type constraints are satisfied.

```scala 
val gaugeSomething = for {
  _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGauge @@ aspCountAll
} yield ()
```

## Histograms

A histogram observes `Double` values and counts the observed values in buckets. Each bucket is defined
by an upper boundary and the count for a bucket with the upper boundary `b` increases by `1` if an observed
value `v` is less or equal to `b`.

As a consequence, all buckets that have a boundary `b1` with `b1 > b` will increase by `1` after observing `v`.

A histogram also keeps track of the overall count of observed values and the sum of all observed values.

By definition, the last bucket is always defined as `Double.MaxValue`, so that the count of observed values in
the last bucket is always equal to the overall count of observed values within the histogram.

To define a histogram aspect, the API requires that the boundaries for the histogram are specified when creating
the aspect.

The mental model for a ZMX histogram is inspired
from [Prometheus](https://prometheus.io/docs/concepts/metric_types/#histogram).

### API

Create a histogram that can be applied to effects producing `Double` values. The values will be counted as outlined
above.

```scala
def histogram(name: String, boundaries: Histogram.Boundaries): Metric.Histogram[Double]
```

### Examples

Create a histogram with 12 buckets: `0..100` in steps of `10` and `Double.MaxValue`. It can be applied to effects
yielding a `Double`.

```scala 
val aspHistogram =
  Metric.histogram("histogram", Histogram.Boundaries.linear(0.0d, 10.0d, 11))
```

Now we can apply the histogram to effects producing `Double`:

```scala 
val histogram = nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram 
```

## Summaries

Similar to a histogram a summary also observes `Double` values. While a histogram directly modifies the bucket counters
and does not keep the individual samples, the summary keeps the observed samples in its internal state. To avoid the set
of samples grow uncontrolled, the summary need to be configured with a maximum age `t` and a maximum size `n`. To
calculate the statistics, maximal `n` samples will be used, all of which are not older than `t`.

Essentially the set of samples is a sliding window over the last observed samples matching the conditions above.

A summary is used to calculate a set of quantiles over the current set of samples. A quantile is defined by a `Double`
value `q`
with `0 <= q <= 1` and resolves to a `Double` as well.

The value of a given quantile `q` is the maximum value `v` out of the current sample buffer with size `n` where at
most `q * n`
values out of the sample buffer are less or equal to `v`.

Typical quantiles for observation are `0.5` (the median) and the `0.95`. Quantiles are very good for monitoring Service
Level Agreements.

The ZMX API also allows summaries to be configured with an error margin `e`. The error margin is applied to the count of
values, so that a
quantile `q` for a set of size `s` resolves to value `v` if the number `n` of values less or equal to `v`
is `(1 -e)q * s <= n <= (1+e)q`.

### API

A metric aspect that adds a value to a summary each time the effect it is applied to succeeds. This aspect can be
applied to effects producing a `Double`.

```scala
def summary(
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double]
): Metric.Summary[Double]
```

### Examples

Create a summary that can hold 100 samples, the max age of the samples is `1 day` and the
error margin is `3%`. The summary should report the `10%`, `50%` and `90%` Quantile.
It can be applied to effects yielding an `Int`.

```scala
val aspSummary =
  Metric.summary("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9)).contramap[Int](_.toDouble)
```

Now we can apply this aspect to an effect producing an `Int`:

```scala
val summary = nextIntBetween(100, 500) @@ aspSummary
```

## Frequencies

Frequencies are used to count the occurrences of distinct string values. For example an application that uses logical
names for its services, the number of invocations for each service can be tracked.

Essentially, a Frequency is a set of related counters sharing the same name and tags. The counters are set
apart from each other by an additional configurable tag. The values of the tag represent the observed
distinct values.

To configure a frequency aspect, the name of the tag holding the distinct values must be configured.

### API

A metric aspect that counts the number of occurrences of each distinct
value returned by the effect it is applied to.

```scala
def frequency(name: String): Metric.Frequency[String]
```

### Examples

Create a Frequency to observe the occurrences of unique Strings.
It can be applied to effects yielding a String.

```scala
val aspSet = Metric.frequency("mySet")
```  

Now we can generate some keys within an effect and start counting the occurrences
for each value.

```scala 
val set = nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspSet
```
