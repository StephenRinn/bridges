package benchmark

import cats.effect.unsafe.implicits.global
import logger.BridgeLogger
import logEvent.LogLevel._
import org.openjdk.jmh.annotations._

class BridgesBenchmark extends BenchmarkBase {

  private var logger: BridgeLogger = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    logger = BridgeLogger.builder(
      minLevel = Info,
      replayAllLogLevel = Warn,
    ).build(NoOpBridgesSink).unsafeRunSync()
  }

  @Benchmark
  def disabledDebug(): Unit =
    logger.debug("hello").unsafeRunSync()

  @Benchmark
  def enabledInfo(): Unit =
    logger.info("hello").unsafeRunSync()

  @Benchmark
  def enabledInfoWithFields(): Unit =
    logger.info(
      "hello",
      "userId" -> "123",
      "operation" -> "benchmark",
    ).unsafeRunSync()
}