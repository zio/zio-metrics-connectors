package zio.metrics.connectors.insight

import java.time.Instant
import java.util.UUID

import zio._
import zio.json._
import zio.metrics._

sealed private[insight] trait KeyTypes {
  val name: String
}

private[insight] object KeyTypes {
  case object Counter   extends KeyTypes { override val name: String = "Counter"   }
  case object Gauge     extends KeyTypes { override val name: String = "Gauge"     }
  case object Frequency extends KeyTypes { override val name: String = "Frequency" }
  case object Histogram extends KeyTypes { override val name: String = "Histogram" }
  case object Summary   extends KeyTypes { override val name: String = "Summary"   }
}

object MetricsMessageImplicits {

  implicit val encInstant: JsonEncoder[Instant] =
    JsonEncoder[Long].contramap(_.toEpochMilli)
  implicit val decInstant: JsonDecoder[Instant] =
    JsonDecoder[Long].map(Instant.ofEpochMilli)

  implicit val encDuration: JsonEncoder[Duration] =
    JsonEncoder[Long].contramap(_.toMillis)
  implicit val decDuration: JsonDecoder[Duration] =
    JsonDecoder[Long].map(Duration.fromMillis)

  implicit val encMetricState: JsonEncoder[MetricState[_]] =
    DeriveJsonEncoder.gen[MetricState[_]]
  implicit val decMetricState: JsonDecoder[MetricState[_]] =
    DeriveJsonDecoder.gen[MetricState[_]]

  implicit val encHistogramBoundaries: JsonEncoder[MetricKeyType.Histogram.Boundaries] =
    DeriveJsonEncoder.gen[MetricKeyType.Histogram.Boundaries]
  implicit val decHistogramBoundaries: JsonDecoder[MetricKeyType.Histogram.Boundaries] =
    DeriveJsonDecoder.gen[MetricKeyType.Histogram.Boundaries]

  implicit val encHistogram: JsonEncoder[MetricKeyType.Histogram] =
    DeriveJsonEncoder.gen[MetricKeyType.Histogram]
  implicit val decHistogram: JsonDecoder[MetricKeyType.Histogram] =
    DeriveJsonDecoder.gen[MetricKeyType.Histogram]

  implicit val encSummary: JsonEncoder[MetricKeyType.Summary] =
    DeriveJsonEncoder.gen[MetricKeyType.Summary]
  implicit val decSummary: JsonDecoder[MetricKeyType.Summary] =
    DeriveJsonDecoder.gen[MetricKeyType.Summary]

  implicit val encMetricLabel: JsonEncoder[MetricLabel] =
    DeriveJsonEncoder.gen[MetricLabel]
  implicit val decMetricLabel: JsonDecoder[MetricLabel] =
    DeriveJsonDecoder.gen[MetricLabel]

  /**
   *  We map a metric key as a [[MetricKeyTransfer]] so that we can
   *  yield the following serialization structure:
   *
   * Example:
   * {{{
   * {
   *    "name": "The name of the key",
   *    "labels": [
   *       {
   *          "key": "pool",
   *          "value": "CodeHeap 'non-nmethods'"
   *       }
   *    ],
   *    "metricType": "Gauge",
   *    details": "{}"
   * }
   * }}}
   *
   * @param name The name of the key
   * @param labels The labels of the key
   * @param metricType The type of the metric the key points to
   */
  case class MetricKeyTransfer(
    name: String,
    labels: Set[MetricLabel],
    metricType: String,
    details: String)

  implicit val encMetricKeyTransfer: JsonEncoder[MetricKeyTransfer] =
    DeriveJsonEncoder.gen[MetricKeyTransfer]

  implicit val decMetricKeyTransfer: JsonDecoder[MetricKeyTransfer] =
    DeriveJsonDecoder.gen[MetricKeyTransfer]

  implicit val encMetricKey: JsonEncoder[MetricKey[Any]] =
    JsonEncoder[MetricKeyTransfer].contramap[MetricKey[Any]] { key =>
      key.keyType match {
        case MetricKeyType.Counter       =>
          MetricKeyTransfer(key.name, key.tags, KeyTypes.Counter.name, "{}")
        case MetricKeyType.Gauge         =>
          MetricKeyTransfer(key.name, key.tags, KeyTypes.Gauge.name, "{}")
        case MetricKeyType.Frequency     =>
          MetricKeyTransfer(key.name, key.tags, KeyTypes.Frequency.name, "{}")
        case hk: MetricKeyType.Histogram =>
          MetricKeyTransfer(key.name, key.tags, KeyTypes.Histogram.name, hk.toJson)
        case sk: MetricKeyType.Summary   =>
          MetricKeyTransfer(key.name, key.tags, KeyTypes.Summary.name, sk.toJson)
        // This should not happen at all
        case _                           => MetricKeyTransfer(key.name, key.tags, "Untyped", "{}")
      }
    }

  implicit val decMetricKey: JsonDecoder[MetricKey[_]] = {
    import KeyTypes._

    JsonDecoder[MetricKeyTransfer].mapOrFail { case MetricKeyTransfer(name, tags, keyType, details) =>
      keyType match {
        case Counter.name   => Right(MetricKey.counter(name).tagged(tags))
        case Gauge.name     => Right(MetricKey.gauge(name).tagged(tags))
        case Frequency.name => Right(MetricKey.frequency(name).tagged(tags))
        case Histogram.name =>
          details.fromJson[MetricKeyType.Histogram].map(hk => MetricKey.histogram(name, hk.boundaries).tagged(tags))
        case Summary.name   =>
          details
            .fromJson[MetricKeyType.Summary]
            .map(sk => MetricKey.summary(name, sk.maxAge, sk.maxSize, sk.error, sk.quantiles).tagged(tags))
        case _              => Left(s"Could not instantiate MetricKey for KeyType <$keyType>")
      }
    }
  }
}

sealed trait ClientMessage

object ClientMessage {

  import MetricsMessageImplicits._

  final case class MetricKeyWithId(id: UUID, key: MetricKey[Any])

  implicit lazy val encMetricKeyWithId: JsonEncoder[MetricKeyWithId]  = DeriveJsonEncoder.gen[MetricKeyWithId]
  implicit lazy val decAMetricKeyWithId: JsonDecoder[MetricKeyWithId] = DeriveJsonDecoder.gen[MetricKeyWithId]

  /**
   * A message sent by the server to announce the metrics currently available.
   */
  final case class AvailableMetrics(keys: Set[MetricKeyWithId]) extends ClientMessage

  implicit lazy val encAvailableMetrics: JsonEncoder[AvailableMetrics] = DeriveJsonEncoder.gen[AvailableMetrics]
  implicit lazy val decAvailableMetrics: JsonDecoder[AvailableMetrics] = DeriveJsonDecoder.gen[AvailableMetrics]

  /**
   * A selection of metric UUIDs
   * @param selection A set of UUIDs
   */
  final case class MetricsSelection(selection: Set[UUID])

  implicit lazy val encMetricsSelection: JsonEncoder[MetricsSelection] =
    DeriveJsonEncoder.gen[MetricsSelection]
  implicit lazy val decMetricsSelection: JsonDecoder[MetricsSelection] =
    DeriveJsonDecoder.gen[MetricsSelection]

  /**
   * The extended metric state that the insight connector is tracking, which also
   * allows us to provide a nicer API.
   *
   * @param id        The UUID of the metric.
   * @param key       The key of the metric.
   * @param state     The state of the metric.
   * @param timestamp The timestamp of when the metric was emitted.
   */
  final case class InsightMetricState(
    id: UUID,
    key: MetricKey[Any],
    state: MetricState[Any],
    timestamp: Instant)

  implicit lazy val encInsightMetricState: JsonEncoder[InsightMetricState] =
    DeriveJsonEncoder.gen[InsightMetricState]
  implicit lazy val decInsightMetricState: JsonDecoder[InsightMetricState] =
    DeriveJsonDecoder.gen[InsightMetricState]

  /**
   * A response sent by the server for a selection of metrics requested by the client
   */
  final case class MetricsResponse(
    states: Set[InsightMetricState])
      extends ClientMessage

  implicit lazy val encMetricsResponse: JsonEncoder[MetricsResponse] = DeriveJsonEncoder.gen[MetricsResponse]
  implicit lazy val decMetricsResponse: JsonDecoder[MetricsResponse] = DeriveJsonDecoder.gen[MetricsResponse]

  implicit lazy val encClientMessage: JsonEncoder[ClientMessage] = DeriveJsonEncoder.gen[ClientMessage]
  implicit lazy val decClientMessage: JsonDecoder[ClientMessage] = DeriveJsonDecoder.gen[ClientMessage]
}
