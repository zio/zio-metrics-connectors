package zio.metrics.connectors.micrometer

/**
 * Settings for the Micrometer connector.
 *
 * @param summaryPercentileDigitsOfPrecision
 *  The number of digits of precision that Micrometer uses for summary percentiles
 */
final case class MicrometerConfig(summaryPercentileDigitsOfPrecision: Int)

/** Default settings for `MicrometerConfig`. */
object MicrometerConfig {

  /** The defaults: three digits of precision for summary percentiles. */
  val default: MicrometerConfig = MicrometerConfig(summaryPercentileDigitsOfPrecision = 3)
}
