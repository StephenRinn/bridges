package util

import cats.effect.{IO, Ref}
import logSink.LogSink

case class LogEntry (
    level: String,
    message: String
)

trait LogSi

class TestLogSink(ref: Ref[IO, Vector[LogEntry]]) extends LogSink {

  override def info(msg: String): IO[Unit] = {
    ref.update(_ :+ LogEntry("INFO:", msg))
  }

  override def warn(msg: String): IO[Unit] =
    ref.update(_ :+ LogEntry("WARN:", msg))

  override def error(msg: String): IO[Unit] =
    ref.update(_ :+ LogEntry("ERROR:", msg))

  def messages: IO[Vector[LogEntry]] = ref.get
}

object TestLogSink {
    def create: IO[TestLogSink] =
      Ref.of[IO, Vector[LogEntry]](Vector.empty).map(new TestLogSink(_))
}
