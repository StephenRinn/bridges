package logger

import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage
import java.util.UUID
import logEvent.LogLevel
import logEvent.LogLevel.{Debug, Info, Warn}
import logSink.LogSink

trait BridgeLogger {
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private def format(storage: IOStorage, msg: String, level: LogLevel = Debug): String = {
    val values =
      if (storage.values.isEmpty) "-"
      else storage.values.map { case (k, v) => s"$k=$v" }.mkString(", ")

    val duration = (storage.endTime, storage.startTime) match {
      case (Some(end), Some(start)) => Some(end - start)
      case _ => None
    }
    s"""[timestamp=${System.currentTimeMillis()}] [level=$level] [cid=${storage.correlationId}]
       | [rid=${storage.requestId}] [duration=$duration] [values=$values] $msg""".stripMargin
  }

  override def info(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.info(format(storage, msg, Info))
    }
  }

  override def warn(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.warn(format(storage, msg, Warn))
    }
  }

  override def error(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg, LogLevel.Error))
    }
  }

  def error(msg: String, e: Throwable): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg, LogLevel.Error) + e.toString)
    }
  }
}
