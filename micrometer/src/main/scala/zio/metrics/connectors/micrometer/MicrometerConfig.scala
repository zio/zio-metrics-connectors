package zio.metrics.connectors.micrometer

final case class MicrometerConfig(summaryPercentileDigitsOfPrecision: Int)
object MicrometerConfig {
  val default: MicrometerConfig = MicrometerConfig(summaryPercentileDigitsOfPrecision = 3)
}
