package logSink

import cats.effect.IO

trait LogSink{
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
}