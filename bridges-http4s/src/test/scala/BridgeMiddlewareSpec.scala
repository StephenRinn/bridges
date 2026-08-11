import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage
import logger._
import munit.CatsEffectSuite
import org.http4s.{Header, HttpApp, Method, Request, Response, Status}
import org.http4s.dsl.io._
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.PrivateMethodTester
import org.typelevel.ci.CIStringSyntax
import util.TestLogSink

class BridgeMiddlewareSpec extends CatsEffectSuite with PrivateMethodTester {

  private def setup: IO[(IOLocal[IOStorage], TestLogSink, BridgeLogger)] = {
    for {
      storage <- IOLocal(IOStorage.empty)
      sink <- TestLogSink.create
      logger <- BridgeLogger.builder().build(sink)
    } yield (storage, sink, logger)
  }

  test("generates missing correlationId") {
    setup.flatMap { case (_, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => Ok("hello"))
      }

      for {
        response <- app.run(Request[IO](Method.GET, uri"/"))
      } yield {
        val header = response.headers.get(ci"X-Correlation-ID").map(_.head.value)

        assert(header.nonEmpty)
        assert(header.get.nonEmpty)
      }
    }
  }

  test("preserves existing correlation id from request header") {
    setup.flatMap { case (_, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => Ok("hello"))
      }
      val request = Request[IO](Method.GET, uri"/")
        .putHeaders(Header.Raw(ci"X-Correlation-ID", "existing-correlation"))

      for {
        response <- app.run(request)
      } yield {
        val header = response.headers.get(ci"X-Correlation-ID").map(_.head.value)
        assertEquals(header, Some("existing-correlation"))
      }
    }
  }

  test("context is available inside of route") {
    val getStorage = PrivateMethod[IO[IOStorage]](Symbol("getStorage"))
    setup.flatMap { case (storage, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp { _ =>
          for {
            ctx <- logger.invokePrivate(getStorage())
            response <- Ok(s"${ctx.requestId}|${ctx.correlationId}")
          } yield response
        }
      }

      val request = Request[IO](Method.GET, uri"/")
        .putHeaders(Header.Raw(ci"X-Correlation-ID", "corr-123"))

      for {
        response <- app.run(request)
        body <- response.as[String]
      } yield {
        val parts = body.split("\\|")

        assertEquals(parts(1), "corr-123")
        assert(parts(0).nonEmpty)
      }
    }
  }

  test("context clears after success") {
    setup.flatMap { case (storage, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => Ok())
      }

      for {
        _ <- app.run(Request[IO](Method.GET, uri"/"))
        ctx <- storage.get
      } yield {
        assertEquals(ctx, IOStorage.empty)
      }
    }
  }

  test("context clears after failure") {
    setup.flatMap { case (storage, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => IO.raiseError[Response[IO]](new RuntimeException("expected failure")))
      }

      for {
        _ <- app.run(Request[IO](Method.GET, uri"/")).attempt
        ctx <- storage.get
      } yield {
        assertEquals(ctx, IOStorage.empty)
      }
    }
  }

  test("middleware does not influence response") {
    setup.flatMap { case (_, _, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => Created("payload"))
      }

      for {
        response <- app.run(Request[IO](Method.GET, uri"/"))
        body <- response.as[String]
      } yield {
        assertEquals(response.status, Status.Created)
        assertEquals(body, "payload")
      }
    }
  }

  test("concurrent requests have unique correlation ids") {
    setup.flatMap { case (_, _, logger) =>
      val app =
        BridgeMiddleware(logger) {
          HttpApp { request =>
            val corr = request.headers.get(ci"X-Correlation-ID").map(_.head.value).getOrElse("")

            Ok(corr)
          }
        }

      val requests =
        List(
          "corr-1",
          "corr-2",
          "corr-3",
          "corr-4",
          "corr-5",
        ).map { id =>
          app.run(
            Request[IO](Method.GET, uri"/")
              .putHeaders(Header.Raw(ci"X-Correlation-ID", id)),
          )
        }

      for {
        responses <- IO.parSequence(requests)
      } yield {
        val corrIds =
          responses.map(_.headers.get(ci"X-Correlation-Id").map(_.head.value).getOrElse(""))

        assertEquals(
          corrIds.toSet,
          Set("corr-1", "corr-2", "corr-3", "corr-4", "corr-5"),
        )
      }
    }
  }

  test("logger writes completion event for successful request") {
    setup.flatMap { case (_, sink, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => Ok())
      }

      for {
        _ <- app.run(Request[IO](Method.GET, uri"/"))
        logs <- sink.messages
      } yield {
        assert(logs.exists(_.message.contains("Request Completed")))
      }
    }
  }

  test("logger writes completion event for successful request") {
    setup.flatMap { case (_, sink, logger) =>
      val app = BridgeMiddleware(logger) {
        HttpApp(_ => IO.raiseError[Response[IO]](new RuntimeException("expected failure")))
      }

      for {
        _ <- app.run(Request[IO](Method.GET, uri"/")).attempt
        logs <- sink.messages
      } yield {
        assert(logs.exists(_.message.contains("Request failed with exception")))
      }
    }
  }

}
