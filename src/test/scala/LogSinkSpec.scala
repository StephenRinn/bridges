import cats.effect.{IO, IOLocal}
import cats.implicits.catsSyntaxParallelTraverse_
import contextStorage.IOStorage
import munit.CatsEffectSuite
import util.{LogEntry, TestLogSink}

class LogSinkSpec extends CatsEffectSuite {
  test("Logger should include correlation id") {

    for {
      storage <- IOLocal(IOStorage.empty)

      sink    <- TestLogSink.create

      logger = new BridgeLoggerImpl(storage, sink)

      _ <- storage.update(_.copy(correlationId = "abc"))

      _ <- logger.info("hello")

      logs <- sink.messages
    } yield {
      assert(logs.size == 1)
      assert(logs.head.message.contains("abc"))
    }
  }
  test("updates are applied"){
    for {
      storage <- IOLocal(IOStorage.empty)

      sink    <- TestLogSink.create

      logger = new BridgeLoggerImpl(storage, sink)

      _ <- storage.update(_.copy(correlationId = "PreUpdateTest"))
      _ <- logger.info("first")

      _ <- storage.update(_.copy(correlationId = "UpdateTest"))
      _ <- logger.info("second")

      logs <- sink.messages
    } yield {
      assert(logs(0).message.contains("PreUpdateTest"))
      assert(logs(1).message.contains("UpdateTest"))
    }
  }

  test("correlationId is propagated across fibers"){
    for {
      storage <- IOLocal(IOStorage.empty)
      sink <- TestLogSink.create
      logger = new BridgeLoggerImpl(storage, sink)

      _ <- storage.update(_.copy(correlationId = "abc"))

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

        logger = new BridgeLoggerImpl(storage, sink)

        _ <- storage.update(
          _.copy(
            correlationId = s"corr-$id",
            requestId = s"req-$id"
          )
        )

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
        request(5)
      ).parSequence

    } yield {

      results.zipWithIndex.foreach {
        case (logs, index) =>

          val id = index + 1

          assertEquals(logs.size, 10)

          assert(
            logs.forall(
              _.message.contains(s"[cid=corr-$id]")
            )
          )

          assert(
            logs.forall(
              _.message.contains(s"[rid=req-$id]")
            )
          )
      }
    }
  }
  test("parallel requests remain isolated") {

    def request(
                 id: Int,
                 storage: IOLocal[IOStorage],
                 logger: BridgeLogger
               ): IO[Unit] =
      storage.set(
        IOStorage(
          requestId = s"req-$id",
          correlationId = s"corr-$id",
          values = Map.empty,
          startTime = None, endTime = None
        )
      ) *>
        List
          .fill(10)(
            IO.cede *> logger.info(s"processing-$id") *> IO.cede
          )
          .parSequence_

    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger = new BridgeLoggerImpl(storage, sink)

      _ <- List(
        request(1, storage, logger),
        request(2, storage, logger),
        request(3, storage, logger),
        request(4, storage, logger),
        request(5, storage, logger)
      ).parSequence

      logs <- sink.messages

    } yield {

      assertEquals(logs.size, 50)

      (1 to 5).foreach { id =>
        val requestLogs =
          logs.filter(_.message.contains(s"[rid=req-$id]"))

        assertEquals(requestLogs.size, 10)

        assert(
          requestLogs.forall(
            _.message.contains(s"[cid=corr-$id]")
          )
        )
      }
    }
  }
  test("massive stress test - correlation ids remain isolated") {

    val RequestCount = 250
    val LogsPerRequest = 20

    def request(
                 id: Int,
                 storage: IOLocal[IOStorage],
                 logger: BridgeLogger
               ): IO[Unit] =
      for {
        _ <- storage.set(
          IOStorage(
            requestId = s"req-$id",
            correlationId = s"corr-$id",
            values = Map.empty,
            startTime = None, endTime = None
          )
        )

        _ <- List
          .fill(LogsPerRequest)(
            (IO.cede *> logger.info(s"request-$id") *> IO.cede)
              .start
              .flatMap(_.joinWithNever)
          )
          .parSequence_

      } yield ()

    for {
      storage <- IOLocal(IOStorage.empty)

      sink <- TestLogSink.create

      logger = new BridgeLoggerImpl(storage, sink)

      _ <- (1 to RequestCount)
        .toList
        .parTraverse_(request(_, storage, logger))

      logs <- sink.messages

    } yield {

      assertEquals(logs.size, RequestCount * LogsPerRequest)

      (1 to RequestCount).foreach { id =>

        val requestLogs =
          logs.filter(_.message.contains(s"[rid=req-$id]"))

        assertEquals(
          requestLogs.size,
          LogsPerRequest,
          s"Incorrect number of logs for request $id"
        )

        assert(
          requestLogs.forall(
            _.message.contains(s"[cid=corr-$id]")
          ),
          s"Correlation id mismatch for request $id"
        )
      }
    }
  }
}
