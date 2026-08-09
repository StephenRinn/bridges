import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage
import logEvent.LogLevel._
import logger.{BridgeLoggerConfig, BridgeLoggerImpl}
import munit.CatsEffectSuite
import util.TestLogSink

class BridgeLoggerSpec extends CatsEffectSuite {

  private def setup: IO[(IOLocal[IOStorage], TestLogSink, BridgeLoggerImpl)] = {
    val bridgeConfig = BridgeLoggerConfig.default
    val updatedBridgeConfig =
      bridgeConfig.copy(
        sampleBelowMinLevel = true,
        bufferBelowMinLevel = true,
        replayAllLogLevel = Info,
      )
    for {
      storage <- IOLocal(IOStorage.empty)
      sink <- TestLogSink.create
      logger = new BridgeLoggerImpl(
        ioStorage = storage,
        sink = sink,
        bridgeLoggerConfig = updatedBridgeConfig,
      )
    } yield (storage, sink, logger)
  }

  test("correctly stores log history for below level logs") {
    setup.flatMap { case (_, sink, logger) =>
      for {
        beforeStorage <- logger.getStorage
        _ <- logger.debug("This is a debug test")
        _ <- logger.trace("This is a trace test")
        afterStorage <- logger.getStorage
        _ <- logger.info("This is the trigger")
        finalStorage <- logger.getStorage
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
