package logSink

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging

class SLF4JSink extends LogSink with LazyLogging {

  override def info(msg: String): IO[Unit] = {
    IO.blocking(logger.info(msg))
  }

  override def warn(msg: String): IO[Unit] = {
    IO.blocking(logger.warn(msg))
  }

  override def error(msg: String): IO[Unit] = {
    IO.blocking(logger.error(msg))
  }
}
