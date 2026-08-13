import cats.effect.Clock
import cats.effect.IO
import cats.effect.IOLocal
import contextStorage.ContextOperations
import contextStorage.IOStorage
import munit.CatsEffectSuite

class ContextOperationSpec extends CatsEffectSuite {

  private def contextOps[A](test: ContextOperations => IO[A]): IO[A] =
    for {
      storage <- IOLocal(IOStorage.empty)
      ctx = new ContextOperations(storage)
      result <- test(ctx)
    } yield result

  test("Context Operations sets an empty storage") {
    contextOps { ctx =>
      ctx.get.map { request =>
        assertEquals(request.requestId, "")
        assertEquals(request.correlationId, "")
        assertEquals(request.startTime, None)
        assertEquals(request.endTime, None)
      }
    }
  }

  test("setRequest stores the request Id correctly") {
    contextOps { ctx =>
      for {
        _ <- ctx.setRequest("abc123")
        request <- ctx.get
      } yield assertEquals(request.requestId, "abc123")
    }
  }

  test("setCorrelation stores the correlationId correctly") {
    contextOps { ctx =>
      for {
        _ <- ctx.setCorrelation("abc123")
        request <- ctx.get
      } yield assertEquals(request.correlationId, "abc123")
    }
  }

  test("markStart stores start time correctly") {
    contextOps { ctx =>
      for {
        clock <- Clock[IO].realTime
        now = clock.toMillis
        _ <- ctx.markStart(now)
        request <- ctx.get
      } yield assertEquals(request.startTime, Some(now))
    }
  }

  test("markEnd stores start time correctly") {
    contextOps { ctx =>
      for {
        clock <- Clock[IO].realTime
        now = clock.toMillis
        _ <- ctx.markEnd(now)
        request <- ctx.get
      } yield assertEquals(request.endTime, Some(now))
    }
  }

  test("context composes correctly") {
    contextOps { ctx =>
      for {
        clockS <- Clock[IO].realTime
        start = clockS.toMillis
        _ <- ctx.markStart(start)
        _ <- ctx.setRequest("req")
        _ <- ctx.setCorrelation("cor")
        clockE <- Clock[IO].realTime
        end = clockE.toMillis
        _ <- ctx.markEnd(end)
        request <- ctx.get
      } yield {
        assertEquals(request.startTime, Some(start))
        assertEquals(request.requestId, "req")
        assertEquals(request.correlationId, "cor")
        assertEquals(request.endTime, Some(end))
      }
    }
  }

  test("context is correctly cleared") {
    contextOps { ctx =>
      for {
        clockS <- Clock[IO].realTime
        start = clockS.toMillis
        _ <- ctx.markStart(start)
        _ <- ctx.setRequest("req")
        _ <- ctx.setCorrelation("cor")
        clockE <- Clock[IO].realTime
        end = clockE.toMillis
        _ <- ctx.markEnd(end)
        _ <- ctx.clear
        request <- ctx.get
      } yield {
        assertEquals(request.startTime, None)
        assertEquals(request.requestId, "")
        assertEquals(request.correlationId, "")
        assertEquals(request.endTime, None)
      }
    }
  }

}
