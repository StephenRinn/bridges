package benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.odin.{Level, Logger}
import io.odin.extras.loggers.ConditionalLogger
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class OdinBenchmark extends BenchmarkBase {

  private val inner: Logger[IO] =
    Logger.noop[IO]

  @Benchmark
  def odinInfo(): Unit =
    inner.info("test message").unsafeRunSync()

  @Benchmark
  def odinConditionalSuccess(): Unit =
    ConditionalLogger
      .withConditional(
        inner = inner,
        minLevelOnError = Level.Error,
        maxBufferSize = None
      )
      .use { logger =>
        logger.info("test message")
      }
      .unsafeRunSync()

  @Benchmark
  def odinConditionalError(): Unit =
    ConditionalLogger
      .withConditional(
        inner = inner,
        minLevelOnError = Level.Error,
        maxBufferSize = None
      )
      .use { logger =>
        logger.info("test message") *>
          IO.raiseError(new RuntimeException("test failure"))
      }
      .attempt
      .unsafeRunSync()
}