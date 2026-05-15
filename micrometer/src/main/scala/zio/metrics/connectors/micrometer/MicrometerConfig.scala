package zio.metrics.connectors.micrometer

/**
 * @param histogramMultiplicator for all histograms (excluding timers):
 *   1. set `.scale` of the micrometer DistributionSummary
 *   1. multiple provided boundaries
 *
 * This parameter is a work-around for the fact that micrometer DistributionSummary
 * doesn't support non-integer boundaries.
 * @see https://github.com/micrometer-metrics/micrometer/issues/6298
 */
final case class MicrometerConfig(
  summaryPercentileDigitsOfPrecision: Int,
  histogramMultiplicator: Int)

object MicrometerConfig {
  val default: MicrometerConfig = MicrometerConfig(
    summaryPercentileDigitsOfPrecision = 3,
    histogramMultiplicator = 1,
  )
}
