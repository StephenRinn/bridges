package benchmark

import ch.qos.logback.classic.{Level => LogbackLevel, Logger}
import org.openjdk.jmh.annotations.{Benchmark, Level => JmhLevel, Setup, State, Scope}
import org.slf4j.LoggerFactory

@State(Scope.Thread)
class Slf4jLogbackBenchmark extends BenchmarkBase {

  private val logger: Logger =
    LoggerFactory
      .getLogger(classOf[Slf4jLogbackBenchmark])
      .asInstanceOf[Logger]

  @Setup(JmhLevel.Trial)
  def setup(): Unit = {
    val context = logger.getLoggerContext

    context.reset()

    val appender = new NoOpAppender
    appender.setContext(context)
    appender.start()

    logger.setLevel(LogbackLevel.INFO)
    logger.setAdditive(false)
    logger.addAppender(appender)
  }

  @Benchmark
  def slf4jLogbackInfo(): Unit =
    logger.info("test message")
}