package logSink

import cats.effect.IO
import logEvent.LogEvent

trait LogSink{
  def info(event: IO[LogEvent]): IO[Unit]
  def warn(event: IO[LogEvent]): IO[Unit]
  def error(event: IO[LogEvent]): IO[Unit]
}