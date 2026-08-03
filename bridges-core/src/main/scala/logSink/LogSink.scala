package logSink

import cats.effect.IO
import logEvent.LogEvent

trait LogSink {
  protected def log(event: LogEvent): IO[Unit]
  def trace(event: LogEvent): IO[Unit] = log(event)
  def debug(event: LogEvent): IO[Unit] = log(event)
  def info(event: LogEvent): IO[Unit] = log(event)
  def warn(event: LogEvent): IO[Unit] = log(event)
  def error(event: LogEvent): IO[Unit] = log(event)
}
