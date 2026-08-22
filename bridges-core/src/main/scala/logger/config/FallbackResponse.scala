package logger.config

import cats.effect.IO
import logEvent.LogField
import logEvent.LogValue

case class FallbackResponse(
    errorFallback: (Throwable, => String, Map[String, LogValue], Seq[LogField]) => IO[Unit] =
      (_, _, _, _) => IO.unit,
    cancelFallback: (=> String, Map[String, LogValue], Seq[LogField]) => IO[Unit] = (_, _, _) =>
      IO.unit,
)
