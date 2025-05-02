package zio.metrics.connectors.statsd

import zio.{ULayer, _}

trait StatsdConfig

trait StatsdClientIpConfig extends StatsdConfig {
  val host: String
  val port: Int
}

case class StatsdIpConfig(host: String, port: Int) extends StatsdClientIpConfig

object StatsdConfig {

  val default: StatsdClientIpConfig =
    StatsdIpConfig("localhost", 8125)

  val defaultLayer: ULayer[StatsdConfig] = ZLayer.succeed(default)
}
