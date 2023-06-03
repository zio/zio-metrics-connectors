package zio.metrics.connectors.micrometer

final case class MicroMeterConfig(summaryPercentileDigitsOfPrecision: Int)
object MicroMeterConfig {
  val default: MicroMeterConfig = MicroMeterConfig(3)
}
