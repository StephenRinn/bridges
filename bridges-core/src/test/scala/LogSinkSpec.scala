import cats.effect.{IO, IOLocal}
import cats.implicits.catsSyntaxParallelTraverse_
import contextStorage.IOStorage
import logEvent.LogLevel.Info
import logger.BridgeLogger
import munit.CatsEffectSuite
import util.{LogEntry, TestLogSink}

class LogSinkSpec extends CatsEffectSuite {
  test("Logger should include correlation id") {

    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger <- BridgeLogger.builder().build(sink)

      _ <- storage.update(_.copy(correlationId = "abc"))

      _ <- logger.info("hello")

      logs <- sink.messages

      storageEnd <- storage.get
    } yield {
      assert(logs.size == 1)
      assert(storageEnd.correlationId == "abc")
    }
  }
  test("updates are applied") {
    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger <- BridgeLogger.builder().withMinLevel(Info).build(sink)

      _ <- logger.setCorrelationId("PreUpdateTest")
      _ <- logger.info("first")

      _ <- logger.setCorrelationId("UpdateTest")
      _ <- logger.info("second")

      logs <- sink.messages
    } yield {
      assert(logs(0).message.contains("PreUpdateTest"))
      assert(logs(1).message.contains("UpdateTest"))
    }
  }

  test("correlationId is propagated across fibers") {
    for {
      storage <- IOLocal(IOStorage.empty)
      sink <- TestLogSink.create
      logger <- BridgeLogger.builder().build(sink)

      _ <- logger.setCorrelationId("abc")

      _ <- logger.info("before")

      fiber <- logger.info("inside fiber").start

      _ <- fiber.join

      logs <- sink.messages
    } yield {
      assert(logs.forall(_.message.contains("abc")))
    }
  }

  test("parallel requests remain isolated") {

    def request(id: Int): IO[Vector[LogEntry]] =
      for {
        storage <- IOLocal(IOStorage.empty)

        sink <- TestLogSink.create

        logger <- BridgeLogger.builder().build(sink)

        _ <- logger.setCorrelationId(s"corr-$id")
        _ <- logger.setRequestId(s"req-$id")

        _ <- List
          .fill(10)(logger.info("processing"))
          .parSequence

        logs <- sink.messages
      } yield logs

    for {

      results <- List(
        request(1),
        request(2),
        request(3),
        request(4),
        request(5),
      ).parSequence

    } yield {

      results.zipWithIndex.foreach { case (logs, index) =>
        val id = index + 1

        assertEquals(logs.size, 10)

        assert(
          logs.forall(
            _.message.contains(s"corr-$id"),
          ),
        )

        assert(
          logs.forall(
            _.message.contains(s"req-$id"),
          ),
        )
      }
    }
  }
  test("parallel requests remain isolated") {

    def request(
        id: Int,
        storage: IOLocal[IOStorage],
        logger: BridgeLogger,
    ): IO[Unit] = {
      for {
        _ <- logger.setCorrelationId(s"corr-$id")
        _ <- logger.setRequestId(s"req-$id")
        _ <- List
          .fill(10)(
            IO.cede *> logger.info(s"processing-$id") *> IO.cede,
          )
          .parSequence_
      } yield ()
    }

    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger <- BridgeLogger.builder().build(sink)

      _ <- List(
        request(1, storage, logger),
        request(2, storage, logger),
        request(3, storage, logger),
        request(4, storage, logger),
        request(5, storage, logger),
      ).parSequence

      logs <- sink.messages

    } yield {

      assertEquals(logs.size, 50)

      (1 to 5).foreach { id =>
        val requestLogs =
          logs.filter(_.message.contains(s"req-$id"))

        assertEquals(requestLogs.size, 10)

        assert(
          requestLogs.forall(
            _.message.contains(s"corr-$id"),
          ),
        )
      }
    }
  }
  test("massive stress test - correlation ids remain isolated") {

    val RequestCount = 250
    val LogsPerRequest = 20

    def request(
        id: Int,
        logger: BridgeLogger,
    ): IO[Unit] =
      for {
        _ <- logger.setCorrelationId(s"corr-$id")
        _ <- logger.setRequestId(s"req-$id")
        _ <- List
          .fill(LogsPerRequest)(
            (IO.cede *> logger.info(s"request-$id") *> IO.cede).start
              .flatMap(_.joinWithNever),
          )
          .parSequence_

      } yield ()

    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger <- BridgeLogger.builder().build(sink)

      _ <- (1 to RequestCount).toList
        .parTraverse_(request(_, logger))

      logs <- sink.messages

    } yield {

      assertEquals(logs.size, RequestCount * LogsPerRequest)

      (1 to RequestCount).foreach { id =>
        val requestLogs =
          logs.filter(_.message.contains(s"req-$id,"))

        assertEquals(
          requestLogs.size,
          LogsPerRequest,
          s"Incorrect number of logs for request $id",
        )

        assert(
          requestLogs.forall(
            _.message.contains(s"corr-$id"),
          ),
          s"Correlation id mismatch for request $id",
        )
      }
    }
  }
}
