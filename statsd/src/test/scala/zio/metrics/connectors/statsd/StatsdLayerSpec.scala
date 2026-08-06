package zio.metrics.connectors.statsd

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import zio._
import zio.metrics._
import zio.metrics.connectors._
import zio.test._
import zio.test.TestAspect._

object StatsdLayerSpec extends ZIOSpecDefault {

  override def spec = suite("The statsdUDP layer should")(
    applyEncoderConfig,
  ) @@ timed @@ withLiveClock @@ TestAspect.timeout(60.seconds)

  private val applyEncoderConfig =
    test("apply the prefix, the constant tags and the suffix configured in the StatsdConfig") {
      val prefix       = "myapp."
      val constantTags = List(MetricLabel("env", "test"), MetricLabel("region", "local"))
      val suffix       = "|c:containerId"
      val name         = "statsdLayerSpecCounter"

      ZIO.scoped[Any] {
        for {
          // Arrange: a UDP server on an ephemeral port the client reports to
          channel  <- listener
          port      = channel.getLocalAddress.asInstanceOf[InetSocketAddress].getPort
          received <- receiveUntil(channel, name).fork

          // Act: run the counter with a statsd client configured with prefix, constant tags and suffix
          datagram <- (ZIO.serviceWithZIO[StatsdClient](_ => Metric.counter(name).increment) *> received.join)
                        .provide(
                          ZLayer.succeed(
                            StatsdConfig(
                              host = "127.0.0.1",
                              port = port,
                              prefix = Some(prefix),
                              constantTags = constantTags,
                              suffix = Some(suffix),
                            ),
                          ),
                          ZLayer.succeed(MetricsConfig(100.millis)),
                          statsdUDP,
                        )

          // Assert
        } yield assertTrue(datagram == s"$prefix$name:1|c|#env:test,region:local$suffix")
      }
    }

  private def listener: ZIO[Scope, Throwable, DatagramChannel] =
    ZIO.fromAutoCloseable(ZIO.attempt {
      val channel = DatagramChannel.open()
      channel.bind(new InetSocketAddress("127.0.0.1", 0))
      channel
    })

  /**
   * Reads datagrams until one mentioning the given metric name is received, so that metrics reported
   * by other tests sharing the metric registry are ignored.
   */
  private def receiveUntil(channel: DatagramChannel, name: String): Task[String] =
    ZIO
      .attemptBlockingInterrupt {
        val buffer = ByteBuffer.allocate(1024)
        channel.receive(buffer)
        buffer.flip()
        val bytes  = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        new String(bytes)
      }
      .repeatUntil(_.contains(name))
}
