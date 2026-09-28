package benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.odin.Level
import io.odin.Logger
import io.odin.LoggerMessage
import io.odin.extras.loggers.ConditionalLogger
import io.odin.formatter.Formatter
import io.odin.loggers.DefaultLogger
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class OdinBenchmark extends BenchmarkBase {

  private val formatter = Formatter.default

  private val inner: Logger[IO] =
    new DefaultLogger[IO](Level.Info) {

      override def withMinimalLevel(level: Level): Logger[IO] =
        this

      override def submit(msg: LoggerMessage): IO[Unit] =
        IO(formatter.format(msg)).void
    }

  private val innerNoFormat: Logger[IO] =
    new DefaultLogger[IO](Level.Info) {

      override def withMinimalLevel(level: Level): Logger[IO] =
        this

      override def submit(msg: LoggerMessage): IO[Unit] =
        IO.unit
    }

  private val message = "hello"

  private val context =
    Map(
      "userId" -> "123",
      "operation" -> "benchmark",
    )

  @Benchmark
  def odinInfo(): Unit =
    inner.info(message).unsafeRunSync()

  @Benchmark
  def odinDisabledDebug(): Unit =
    inner.debug(message).unsafeRunSync()

  @Benchmark
  def odinInfoWithContext(): Unit =
    inner.info(message, context).unsafeRunSync()

  @Benchmark
  def odinConditionalSuccess(): Unit =
    ConditionalLogger
      .withConditional(
        inner = inner,
        minLevelOnError = Level.Error,
        maxBufferSize = None,
      )
      .use { logger =>
        logger.debug(message) *>
          logger.debug(message) *>
          logger.info(message) *>
          logger.info("Request Completed")
      }
      .unsafeRunSync()

  @Benchmark
  def odinConditionalError(): Unit =
    ConditionalLogger
      .withConditional(
        inner = inner,
        minLevelOnError = Level.Error,
        maxBufferSize = None,
      )
      .use { logger =>
        logger.debug(message) *>
          logger.debug(message) *>
          logger.error(message) *>
          logger.info("Request Completed")
      }
      .unsafeRunSync()

  @Benchmark
  def odinConditionalSuccessNoFormat(): Unit =
    ConditionalLogger
      .withConditional(
        inner = innerNoFormat,
        minLevelOnError = Level.Error,
        maxBufferSize = None,
      )
      .use { logger =>
        logger.debug(message) *>
          logger.debug(message) *>
          logger.info(message) *>
          logger.info("Request Completed")
      }
      .unsafeRunSync()

  @Benchmark
  def odinConditionalErrorNoFormat(): Unit =
    ConditionalLogger
      .withConditional(
        inner = innerNoFormat,
        minLevelOnError = Level.Error,
        maxBufferSize = None,
      )
      .use { logger =>
        logger.debug(message) *>
          logger.debug(message) *>
          logger.error(message) *>
          logger.info("Request Completed")
      }
      .unsafeRunSync()

  @Benchmark def odinConditionalBufferReplayAtScale10(): Unit = ConditionalLogger
    .withConditional(inner = innerNoFormat, minLevelOnError = Level.Error, maxBufferSize = Some(50))
    .use { logger =>
      (1 to 10).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      } >> logger.error(message) >> logger.info("Request Completed")
    }
    .unsafeRunSync()

  @Benchmark def odinConditionalBufferReplayAtScale25(): Unit = ConditionalLogger
    .withConditional(inner = innerNoFormat, minLevelOnError = Level.Error, maxBufferSize = Some(50))
    .use { logger =>
      (1 to 25).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      } >> logger.error(message) >> logger.info("Request Completed")
    }
    .unsafeRunSync()

  @Benchmark def odinConditionalBufferReplayAtScale50(): Unit = ConditionalLogger
    .withConditional(inner = innerNoFormat, minLevelOnError = Level.Error, maxBufferSize = Some(50))
    .use { logger =>
      (1 to 50).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      } >> logger.error(message) >> logger.info("Request Completed")
    }
    .unsafeRunSync()

  @Benchmark def odinConditionalBufferReplayAtScale100(): Unit = ConditionalLogger
    .withConditional(
      inner = innerNoFormat,
      minLevelOnError = Level.Error,
      maxBufferSize = Some(100),
    )
    .use { logger =>
      (1 to 100).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      } >> logger.error(message) >> logger.info("Request Completed")
    }
    .unsafeRunSync()
}
