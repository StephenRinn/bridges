package logSink

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.syntax.EncoderOps
import logEvent.LogEvent
import JsonHelpers._

class JSONSink extends LogSink{
  private def toLoggingJson(logEvent: LogEvent): String = {
    val ctx = logEvent.context
    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => end - start
      case _ => -1
    }

    Json.obj(
      "timestamp" -> logEvent.timestamp.asJson,
      "cid" -> ctx.correlationId.asJson,
      "rid" -> ctx.requestId.asJson,
      "duration" -> duration.asJson,
      "level" -> logEvent.level.toString.asJson,
      "message" -> logEvent.message.asJson,
      "error" -> logEvent.throwable.orNull.asJson
    ).noSpaces
  }

  private def printJson(event: IO[LogEvent]): IO[Unit] = {
    for{
      log <- event
    } yield
    IO.blocking(println(toLoggingJson(log)))
  }

  override def trace(event: IO[LogEvent]): IO[Unit] = {
    printJson(event)
  }

  override def debug(event: IO[LogEvent]): IO[Unit] = {
    printJson(event)
  }

  override def info(event: IO[LogEvent]): IO[Unit] = {
    printJson(event)
  }

  override def warn(event: IO[LogEvent]): IO[Unit] = {
    printJson(event)
  }

  override def error(event: IO[LogEvent]): IO[Unit] = {
    printJson(event)
  }
}

object JsonHelpers {
  implicit val throwableEncoder: Encoder[Throwable] = {
    Encoder.instance{ t =>
      Json.obj(
        "message" -> t.getMessage.asJson,
        "type" -> t.getClass.getName.asJson,
        "stacktrace" -> t.getStackTrace.map(s => s.toString.asJson).asJson
      )
    }
  }
}