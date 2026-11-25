package zio.metrics.connectors.newrelic

import java.time.Duration

import zio._

/**
 * Settings for the New Relic publisher: the API key, the endpoint, the largest number of metrics in one request, and how
 * long a batch that is not full waits before it is sent.
 *
 * @param apiKey
 *  The New Relic API key, sent in the `Api-Key` header
 * @param newRelicURI
 *  The endpoint that metrics are posted to
 * @param maxMetricsPerRequest
 *  The largest number of metrics in one request
 * @param maxPublishingDelay
 *  How long a batch that is not full waits before it is sent
 */
final case class NewRelicConfig(
  apiKey: String,
  newRelicURI: NewRelicUri,
  maxMetricsPerRequest: Int,
  maxPublishingDelay: Duration)

/** Loads `NewRelicConfig` from environment variables. */
object NewRelicConfig {

  // TODO: Use timecode to try and figure out the NR endpoint
  /**
   * Loads the settings from the environment, as a layer. When `NEW_RELIC_API_KEY` is not set, the layer dies with an
   * `IllegalArgumentException`, which is a defect and not a failure. It also dies when
   * `NEW_RELIC_MAX_METRICS_PER_REQUEST` is not an integer, or when `NEW_RELIC_MAX_PUBLISHING_DELAY` is not an ISO-8601
   * duration.
   *
   * ===Environment Variables===
   *
   *  - '''`NEW_RELIC_API_KEY`''': Your New Relic API Key.  '''Required'''.
   *  - '''`NEW_RELIC_URI`''':     The New Relic Metric API URI.  '''Optional'''.  Defaults to `https://metric-api.newrelic.com/metric/v1`.
   *  - '''`NEW_RELIC_MAX_METRICS_PER_REQUEST`''': The largest number of metrics in one request.  '''Optional'''.  Defaults to `500`.
   *  - '''`NEW_RELIC_MAX_PUBLISHING_DELAY`''': How long a batch that is not full waits, as an ISO-8601 duration.  '''Optional'''.  Defaults to `PT5S`.
   *
   * REF: [[https://docs.newrelic.com/docs/data-apis/ingest-apis/metric-api/report-metrics-metric-api/#api-endpoint New Relic's Metric API Doc]]
   */
  val fromEnvLayer: ZLayer[Any, Nothing, NewRelicConfig] =
    ZLayer
      .fromZIO(for {
        apiKey               <- System.env(envApiKey).someOrFail(new IllegalArgumentException("APIKey is missing for New Relic"))
        newRelicUri          <- System.env(envMetricsUri).map(_.map(NewRelicUri.Custom.apply)).map(_.getOrElse(NewRelicUri.NA))
        maxMetricsPerRequest <- System.envOrElse(envMaxMetricsPerRequest, "500").map(_.toInt)
        maxPublishingDelay   <- System.envOrElse(envMaxPublishingDelay, "PT5S").map(Duration.parse)
      } yield NewRelicConfig(apiKey, newRelicUri, maxMetricsPerRequest, maxPublishingDelay))
      .orDie

  /** Same as `fromEnvLayer`, but always uses the EU endpoint, so `NEW_RELIC_URI` is ignored. */
  val fromEnvEULayer: ZLayer[Any, Nothing, NewRelicConfig] =
    fromEnvLayer.project(_.copy(newRelicURI = NewRelicUri.EU))

  private lazy val envApiKey               = "NEW_RELIC_API_KEY"
  private lazy val envMetricsUri           = "NEW_RELIC_URI"
  private lazy val envMaxMetricsPerRequest = "NEW_RELIC_MAX_METRICS_PER_REQUEST"
  private lazy val envMaxPublishingDelay   = "NEW_RELIC_MAX_PUBLISHING_DELAY"
}
