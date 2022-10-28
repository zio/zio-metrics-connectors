package zio.metrics.connectors.insight

import java.time.Instant

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
   *  We map a metric key as a [[MetricKeySerialization]] so that we can
   *  yield the following serialization structure:
   *
   * Example:
   * {{{
   * {
   *    "name": "The name of the key",
   *    "tags": [
   *       {
   *          "key": "pool",
   *          "value": "CodeHeap 'non-nmethods'"
   *       }
   *    ],
   *    "type": "Gauge",
   *    "details": {}
   * }
   * }}}
   *
   * @param name The name of the key
   * @param tags The tags of the key
   * @param keyType The metric Key as a String
   * @param The Json encoded Key details if needed
   */
  case class MetricKeySerialization(
    name: String,
    tags: Set[MetricLabel],
    keyType: String,
    details: String)

  implicit val encMetricKeySerialization: JsonEncoder[MetricKeySerialization] =
    DeriveJsonEncoder.gen[MetricKeySerialization]

  implicit val decMetricKeySerialization: JsonDecoder[MetricKeySerialization] =
    DeriveJsonDecoder.gen[MetricKeySerialization]

  implicit val encMetricKey: JsonEncoder[MetricKey[Any]] =
    JsonEncoder[MetricKeySerialization].contramap[MetricKey[Any]] { key =>
      key.keyType match {
        case MetricKeyType.Counter       =>
          MetricKeySerialization(key.name, key.tags, KeyTypes.Counter.name, "{}")
        case MetricKeyType.Gauge         =>
          MetricKeySerialization(key.name, key.tags, KeyTypes.Gauge.name, "{}")
        case MetricKeyType.Frequency     =>
          MetricKeySerialization(key.name, key.tags, KeyTypes.Frequency.name, "{}")
        case hk: MetricKeyType.Histogram =>
          MetricKeySerialization(key.name, key.tags, KeyTypes.Histogram.name, hk.toJson)
        case sk: MetricKeyType.Summary   =>
          MetricKeySerialization(key.name, key.tags, KeyTypes.Summary.name, sk.toJson)
        // This should not happen at all
        case _                           => MetricKeySerialization(key.name, key.tags, "Untyped", "{}")
      }
    }

  implicit val decMetricKey: JsonDecoder[MetricKey[_]] = {
    import KeyTypes._

    JsonDecoder[MetricKeySerialization].mapOrFail { case MetricKeySerialization(name, tags, keyType, details) =>
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

  /**
   * A message sent by the server to announce the metrics currently available. Also used to request
   * a selection of metrics that the client is interested in.
   */
  final case class AvailableMetrics(keys: Set[MetricKey[Any]]) extends ClientMessage

  implicit lazy val encAvailableMetrics: JsonEncoder[AvailableMetrics] = DeriveJsonEncoder.gen[AvailableMetrics]
  implicit lazy val decAvailableMetrics: JsonDecoder[AvailableMetrics] = DeriveJsonDecoder.gen[AvailableMetrics]

  /**
   * A response sent by the server for a selection of metrics requested by the client
   */
  final case class MetricsResponse(
    states: Set[(MetricKey[Any], MetricState[Any])])
      extends ClientMessage

  implicit lazy val encMetricsResponse: JsonEncoder[MetricsResponse] = DeriveJsonEncoder.gen[MetricsResponse]
  implicit lazy val decMetricsResponse: JsonDecoder[MetricsResponse] = DeriveJsonDecoder.gen[MetricsResponse]

  implicit lazy val encClientMessage: JsonEncoder[ClientMessage] = DeriveJsonEncoder.gen[ClientMessage]
  implicit lazy val decClientMessage: JsonDecoder[ClientMessage] = DeriveJsonDecoder.gen[ClientMessage]
}
