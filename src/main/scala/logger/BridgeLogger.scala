package logger

import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage
import logSink.LogSink

trait BridgeLogger {
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private def format(storage: IOStorage, msg: String, level: String = "OFF"): String = {
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
      sink.info(format(storage, msg, "INFO"))
    }
  }

  override def warn(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.warn(format(storage, msg, "WARN"))
    }
  }

  override def error(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg, "ERROR"))
    }
  }

  def error(msg: String, e: Throwable): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg, "ERROR") + e.toString)
    }
  }
}
