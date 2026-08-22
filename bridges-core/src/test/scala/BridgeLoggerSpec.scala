import cats.effect.IO
import contextStorage.IOStorage
import logEvent.LogLevel._
import logger.BridgeLogger
import logger.config.BridgeLoggerConfig
import munit.CatsEffectSuite
import org.scalatest.PrivateMethodTester
import util.TestLogSink

class BridgeLoggerSpec extends CatsEffectSuite with PrivateMethodTester {

  private def setup(sampleRate: Float = 1.0f): IO[(TestLogSink, BridgeLogger)] = {
    for {
      sink <- TestLogSink.create
      _ <- sink.reset
      logger <- BridgeLogger
        .builder()
        .sampleRate(sampleRate)
        .sampleBelowMinLevel(false)
        .bufferBelowMinLevel(true)
        .replayAllLogLevel(Warn)
        .build(sink)
    } yield (sink, logger)
  }

  test("correctly stores log history for below level logs") {
    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup().flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debug("This is a debug test")
        _ <- logger.trace("This is a trace test")
        afterStorage <- logger.invokePrivate(getStorage())
        _ <- logger.warn("This is the trigger")
        finalStorage <- logger.invokePrivate(getStorage())
        messages <- sink.messages
      } yield {
        assertEquals(beforeStorage.rebuildLog.size, 0)
        assertEquals(afterStorage.rebuildLog.size, 2)
        assertEquals(finalStorage.rebuildLog.size, 0)
        assertEquals(messages.size, 3)
        assert(messages.head.level == Debug)
      }
    }
  }

  test("implicit config is correctly applied") {
    import logger.implicits.BridgeLoggerImplicits._

    implicit val config: BridgeLoggerConfig =
      BridgeLoggerConfig(minLevel = Debug, bufferBelowMinLevel = true)

    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup().flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debugImpl("This is a debug test")
        _ <- logger.traceImpl("This is a trace test")
        afterStorage <- logger.invokePrivate(getStorage())
        _ <- logger.infoImpl("This is an info test, not trigger for impl")
        infoStorage <- logger.invokePrivate(getStorage())
        _ <- logger.warnImpl("This is the trigger")
        finalStorage <- logger.invokePrivate(getStorage())
        messages <- sink.messages
      } yield {
        assertEquals(beforeStorage.rebuildLog.size, 0)
        assertEquals(afterStorage.rebuildLog.size, 1)
        assertEquals(infoStorage.rebuildLog.size, 1)
        assertEquals(finalStorage.rebuildLog.size, 0)
        assertEquals(messages.size, 4)
        assert(messages.head.level == Debug)
      }
    }
  }

  test("implicit duplicate on buffer correctly") {
    import logger.implicits.BridgeLoggerImplicits._

    implicit val config: BridgeLoggerConfig = BridgeLoggerConfig(
      minLevel = Debug,
      bufferBelowMinLevel = true,
      duplicateEntriesOnBufferDump = true,
    )

    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup().flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debugImpl("This is a debug test")
        _ <- logger.traceImpl("This is a trace test")
        msg1 <- sink.messages
        afterStorage <- logger.invokePrivate(getStorage())
        _ <- logger.infoImpl("This is an info test, not trigger for impl")
        infoStorage <- logger.invokePrivate(getStorage())
        _ <- logger.warnImpl("This is the trigger")
        finalStorage <- logger.invokePrivate(getStorage())
        messages <- sink.messages
      } yield {
        assertEquals(msg1.size, 1)
        assertEquals(beforeStorage.rebuildLog.size, 0)
        assertEquals(afterStorage.rebuildLog.size, 2)
        assertEquals(infoStorage.rebuildLog.size, 3)
        assertEquals(finalStorage.rebuildLog.size, 0)
        assertEquals(messages.size, 6)
        assert(messages.head.level == Debug)
      }
    }
  }

  test("Zero sample rate functions as expected") {
    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup(0.0f).flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debug("This is a debug test")
        _ <- logger.trace("This is a trace test")
        _ <- logger.info("this shouldn't print")
        finalStorage <- logger.invokePrivate(getStorage())
        messages <- sink.messages
      } yield {
        assertEquals(beforeStorage.rebuildLog.size, 0)
        assertEquals(finalStorage.rebuildLog.size, 3)
        assertEquals(messages.size, 0)
      }
    }
  }

  test("withConfig changes the config values") {
    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup().flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debug("This is a debug test")
        _ <- logger.trace("This is a trace test")
        _ <- logger.info("this should print")
        _ <- logger.withConfig(minLevel = Some(Debug))
        _ <- logger.debug("this should print")
        finalStorage <- logger.invokePrivate(getStorage())
        messages <- sink.messages
      } yield {
        assertEquals(beforeStorage.rebuildLog.size, 0)
        assertEquals(finalStorage.rebuildLog.size, 2)
        assertEquals(messages.size, 2)
      }
    }
  }
}
