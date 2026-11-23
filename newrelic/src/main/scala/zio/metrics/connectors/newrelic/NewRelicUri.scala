package zio.metrics.connectors.newrelic

/** The endpoint of the New Relic Metric API. */
sealed trait NewRelicUri {

  /** The URL that metrics are posted to. */
  def endpoint: String
}

/** The known New Relic endpoints, and a custom one. */
object NewRelicUri {

  /** A custom endpoint, given by its URL. */
  final case class Custom(override val endpoint: String) extends NewRelicUri

  /** The default endpoint, `https://metric-api.newrelic.com/metric/v1`. */
  case object NA extends NewRelicUri { override val endpoint = "https://metric-api.newrelic.com/metric/v1" }

  /** The EU endpoint, `https://metric-api.eu.newrelic.com/metric/v1`. */
  case object EU extends NewRelicUri { override val endpoint = "https://metric-api.eu.newrelic.com/metric/v1" }
}
