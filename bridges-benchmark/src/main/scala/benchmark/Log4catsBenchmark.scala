package benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.{Level => LogbackLevel}
import jdk.jpackage.internal.Arguments.CLIOptions.context
import org.openjdk.jmh.annotations._
import org.slf4j.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

@State(Scope.Thread)
class Log4catsBenchmark extends BenchmarkBase {

  private val underlyingLogger: Logger =
    LoggerFactory
      .getLogger(classOf[Log4catsBenchmark])
      .asInstanceOf[Logger]

  private var logger: org.typelevel.log4cats.SelfAwareStructuredLogger[IO] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val context = underlyingLogger.getLoggerContext

    context.reset()

    val appender = new NoOpAppender
    appender.setContext(context)
    appender.start()

    underlyingLogger.setLevel(LogbackLevel.INFO)
    underlyingLogger.setAdditive(false)
    underlyingLogger.addAppender(appender)

    logger = Slf4jFactory.create[IO].getLogger
  }

  @Benchmark
  def log4catsInfo(): Unit =
    logger.info("test message").unsafeRunSync()

  @Benchmark
  def log4catsDisabledDebug(): Unit =
    logger.debug("test message").unsafeRunSync()

  private val context = Map("userId" -> "123", "operation" -> "benchmark")

  @Benchmark
  def log4catsInfoWithContext(): Unit =
    logger.info(context)("test message").unsafeRunSync()
}
