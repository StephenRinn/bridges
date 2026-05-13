import cats.effect.IO
import cats.effect.IOLocal
import contextStorage.IOStorage
import logSink.LogSink

trait BridgeLogger {
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private def format(storage: IOStorage, msg: String): String = {
    val values =
      if (storage.values.isEmpty) "-"
      else storage.values.map { case (k, v) => s"$k=$v" }.mkString(", ")
    s"[cid=${storage.correlationId}] [rid=${storage.requestId}] [values=$values] $msg"
  }

  override def info(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.info(format(storage, msg))
    }
  }

  override def warn(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.warn(format(storage, msg))
    }
  }

  override def error(msg: String): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg))
    }
  }

  def error(msg: String, e: Throwable): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg) + e.toString)
    }
  }
}
