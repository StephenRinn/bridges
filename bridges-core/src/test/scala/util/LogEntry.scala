package util

import cats.effect.IO
import cats.effect.Ref
import logEvent.LogLevel._
import logEvent._
import logSink.LogSink

case class LogEntry(
    level: LogLevel,
    message: String,
)

class TestLogSink(ref: Ref[IO, Vector[LogEntry]]) extends LogSink {

  def log(event: LogEvent): IO[Unit] = {
    event.level match {
      case LogLevel.Trace => trace(event)
      case LogLevel.Debug => debug(event)
      case LogLevel.Info => info(event)
      case LogLevel.Warn => warn(event)
      case LogLevel.Error => error(event)
    }
  }

  def trace(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Trace, event.toString))
    } yield ()
  }

  def debug(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Debug, event.toString))
    } yield ()
  }

  def info(event: LogEvent): IO[Unit] = {
    for {
      _ <- ref.update(_ :+ LogEntry(Info, event.toString))
    } yield ()
  }

  def warn(event: LogEvent): IO[Unit] =
    for {
      _ <- ref.update(_ :+ LogEntry(Warn, event.toString))
    } yield ()

  def error(event: LogEvent): IO[Unit] =
    for {
      _ <- ref.update(_ :+ LogEntry(Error, event.toString))
    } yield ()

  def messages: IO[Vector[LogEntry]] = ref.get
}

object TestLogSink {
  def create: IO[TestLogSink] =
    Ref.of[IO, Vector[LogEntry]](Vector.empty).map(new TestLogSink(_))
}
