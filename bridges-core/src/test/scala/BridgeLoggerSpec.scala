import cats.effect.IO
import contextStorage.IOStorage
import logEvent.LogLevel._
import logger.BridgeLogger
import munit.CatsEffectSuite
import org.scalatest.PrivateMethodTester
import util.TestLogSink

class BridgeLoggerSpec extends CatsEffectSuite with PrivateMethodTester {

  private def setup: IO[(TestLogSink, BridgeLogger)] = {
    for {
      sink <- TestLogSink.create
      logger <- BridgeLogger
        .builder()
        .sampleBelowMinLevel(true)
        .bufferBelowMinLevel(true)
        .replayAllLogLevel(Info)
        .build(sink)
    } yield (sink, logger)
  }

  test("correctly stores log history for below level logs") {
    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup.flatMap { case (sink, logger) =>
      for {
        beforeStorage <- logger.invokePrivate(getStorage())
        _ <- logger.debug("This is a debug test")
        _ <- logger.trace("This is a trace test")
        afterStorage <- logger.invokePrivate(getStorage())
        _ <- logger.info("This is the trigger")
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
}
