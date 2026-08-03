package util

import cats.effect.{IO, Ref}
import logEvent.LogLevel._
import logEvent._
import logSink.LogSink

case class LogEntry(
    level: LogLevel,
    message: String,
)

class TestLogSink(ref: Ref[IO, Vector[LogEntry]]) extends LogSink {

  protected def log(event: LogEvent): IO[Unit] = IO()

  override def trace(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Trace, event.toString))
    } yield ()
  }

  override def debug(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Debug, event.toString))
    } yield ()
  }

  override def info(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Info, event.toString))
    } yield ()
  }

  override def warn(event: LogEvent): IO[Unit] =
    for {
      _ <- ref.update(_ :+ LogEntry(Warn, event.toString))
    } yield ()

  override def error(event: LogEvent): IO[Unit] =
    for {
      _ <- ref.update(_ :+ LogEntry(Error, event.toString))
    } yield ()

  def messages: IO[Vector[LogEntry]] = ref.get
}

object TestLogSink {
  def create: IO[TestLogSink] =
    Ref.of[IO, Vector[LogEntry]](Vector.empty).map(new TestLogSink(_))
}
