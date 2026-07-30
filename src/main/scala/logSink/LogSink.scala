package logSink

import cats.effect.IO
import logEvent.LogEvent

trait LogSink{
  def trace(event: IO[LogEvent]): IO[Unit]
  def debug(event: IO[LogEvent]): IO[Unit]
  def info(event: IO[LogEvent]): IO[Unit]
  def warn(event: IO[LogEvent]): IO[Unit]
  def error(event: IO[LogEvent]): IO[Unit]
}