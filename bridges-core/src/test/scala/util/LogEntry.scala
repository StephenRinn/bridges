package util

import cats.effect.{IO, Ref}
import logEvent.LogEvent
import logSink.LogSink

case class LogEntry (
    level: String,
    message: String
)

trait LogSi

class TestLogSink(ref: Ref[IO, Vector[LogEntry]]) extends LogSink {

  protected def log(event: LogEvent): IO[Unit] = IO()

  override def trace(event: LogEvent): IO[Unit] = {
    for{
      _ <- ref.update(_ :+ LogEntry("TRACE:", event.toString))
    } yield()
  }

  override def debug(event: LogEvent): IO[Unit] = {
    for{
      _ <- ref.update(_ :+ LogEntry("DEBUG:", event.toString))
    } yield()
  }

  override def info(event: LogEvent): IO[Unit] = {
    for{
      _ <- ref.update(_ :+ LogEntry("INFO:", event.toString))
    } yield()
  }

  override def warn(event: LogEvent): IO[Unit] =
    for{
      _ <- ref.update(_ :+ LogEntry("WARN:", event.toString))
    } yield()


  override def error(event: LogEvent): IO[Unit] =
    for{
      _ <- ref.update(_ :+ LogEntry("ERROR:", event.toString))
    } yield()



  def messages: IO[Vector[LogEntry]] = ref.get
}

object TestLogSink {
    def create: IO[TestLogSink] =
      Ref.of[IO, Vector[LogEntry]](Vector.empty).map(new TestLogSink(_))
}
