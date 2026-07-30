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


  override def trace(event: IO[LogEvent]): IO[Unit] = {
    for{
      eventT <- event
      _ <- ref.update(_ :+ LogEntry("TRACE:", eventT.toString))
    } yield()
  }

  override def debug(event: IO[LogEvent]): IO[Unit] = {
    for{
      eventT <- event
      _ <- ref.update(_ :+ LogEntry("DEBUG:", eventT.toString))
    } yield()
  }

  override def info(event: IO[LogEvent]): IO[Unit] = {
    for{
      eventT <- event
      _ <- ref.update(_ :+ LogEntry("INFO:", eventT.toString))
    } yield()
  }

  override def warn(event: IO[LogEvent]): IO[Unit] =
    for{
      eventT <- event
      _ <- ref.update(_ :+ LogEntry("WARN:", eventT.toString))
    } yield()


  override def error(event: IO[LogEvent]): IO[Unit] =
    for{
      eventT <- event
      _ <- ref.update(_ :+ LogEntry("ERROR:", eventT.toString))
    } yield()



  def messages: IO[Vector[LogEntry]] = ref.get
}

object TestLogSink {
    def create: IO[TestLogSink] =
      Ref.of[IO, Vector[LogEntry]](Vector.empty).map(new TestLogSink(_))
}
