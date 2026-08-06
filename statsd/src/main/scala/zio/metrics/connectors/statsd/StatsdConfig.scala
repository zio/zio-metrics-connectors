package zio.metrics.connectors.statsd

import zio.{ULayer, _}
import zio.metrics.MetricLabel

/**
 * StatsD specific configuration
 *
 * @param host
 *  Agent host name
 * @param port
 *  Agent port
 * @param prefix
 *  An optional prefix that is prepended to every metric name. Any separator has to be part of the value,
 *  e.g. `Some("myapp.")` turns `myCounter` into `myapp.myCounter`
 * @param constantTags
 *  Tags that are added to every metric that is sent
 * @param suffix
 *  An optional suffix that is appended to every datagram, after the tags
 */
final case class StatsdConfig(
  host: String,
  port: Int,
  prefix: Option[String] = None,
  constantTags: List[MetricLabel] = Nil,
  suffix: Option[String] = None)

/**
 * Configuration for sending metrics over a unix domain socket (UDS)
 *
 * @param path
 *  Path of the unix domain socket the agent is listening on
 * @param prefix
 *  An optional prefix that is prepended to every metric name. Any separator has to be part of the value,
 *  e.g. `Some("myapp.")` turns `myCounter` into `myapp.myCounter`
 * @param constantTags
 *  Tags that are added to every metric that is sent
 * @param suffix
 *  An optional suffix that is appended to every datagram, after the tags
 */
final case class DatagramSocketConfig(
  path: String,
  prefix: Option[String] = None,
  constantTags: List[MetricLabel] = Nil,
  suffix: Option[String] = None)

object StatsdConfig {

  val default: StatsdConfig =
    StatsdConfig("localhost", 8125)

  val defaultLayer: ULayer[StatsdConfig] = ZLayer.succeed(default)
}
