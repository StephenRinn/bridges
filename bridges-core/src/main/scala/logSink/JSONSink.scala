package logSink

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.syntax.EncoderOps
import logEvent.LogEvent
import logSink.JsonHelpers._

class JSONSink extends LogSink {
  private def toLoggingJson(logEvent: LogEvent): String = {
    val ctx = logEvent.context
    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => end - start
      case _ => -1
    }

    Json
      .obj(
        "timestamp" -> logEvent.timestamp.asJson,
        "cid" -> ctx.correlationId.asJson,
        "rid" -> ctx.requestId.asJson,
        "duration" -> duration.asJson,
        "values" -> ctx.values.asJson,
        "level" -> logEvent.level.toString.asJson,
        "message" -> logEvent.message.asJson,
        "error" -> logEvent.throwable.orNull.asJson,
      )
      .noSpaces
  }

  override protected def log(event: LogEvent): IO[Unit] = {
    IO.blocking(println(toLoggingJson(event)))
  }
}

object JsonHelpers {
  implicit val throwableEncoder: Encoder[Throwable] = {
    Encoder.instance { t =>
      Json.obj(
        "message" -> t.getMessage.asJson,
        "type" -> t.getClass.getName.asJson,
        "stacktrace" -> t.getStackTrace.map(s => s.toString.asJson).asJson,
      )
    }
  }
}
