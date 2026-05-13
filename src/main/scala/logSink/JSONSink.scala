package logSink

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.EncoderOps

class JSONSink extends LogSink{
  private def toLoggingJson(severity: String, msg: String): String = {
    Json.obj(
      "severity" -> severity.asJson,
      "message" -> msg.asJson,
    ).noSpaces
  }

  override def info(msg: String): IO[Unit] = {
    IO.blocking(println(toLoggingJson("info", msg)))
  }

  override def warn(msg: String): IO[Unit] = {
    IO.blocking(println(toLoggingJson("warn", msg)))
  }

  override def error(msg: String): IO[Unit] = {
    IO.blocking(println(toLoggingJson("error", msg)))
  }
}
