package logSink

import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage


class IOBridgeSink(ioStorage: IOLocal[IOStorage]) extends LogSink {

  override def info(msg: String): IO[Unit] = {
    IO.blocking {
      println(msg)
    }
  }

  override def warn(msg: String): IO[Unit] = {
    IO.blocking {
      println(msg)
    }
  }

  override def error(msg: String): IO[Unit] = {
    IO.blocking {
      println(msg)
    }
  }
}
