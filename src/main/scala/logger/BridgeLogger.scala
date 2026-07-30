package logger

import cats.effect.kernel.Outcome
import cats.effect.{Clock, IO, IOLocal}
import contextStorage.{ContextOperations, IOStorage}
import java.util.UUID
import logEvent.LogLevel
import logEvent.LogLevel.{Debug, Info, Warn}
import logSink.LogSink

trait BridgeLogger {
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
  def withRequest[A](correlationId: String = UUID.randomUUID().toString)(implicit fa: IO[A]): IO[A]
  def updateValues(key: String, value: String): IO[Unit]
  def setCorrelationId(id: String): IO[Unit]
  def setRequestId(id: String): IO[Unit]
  def getIOStorage: IO[IOStorage]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private val ctxOp: ContextOperations = new ContextOperations(ioStorage)

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

  override def error(msg: String, e: Throwable): IO[Unit] = {
    ioStorage.get.flatMap { storage =>
      sink.error(format(storage, msg, LogLevel.Error) + e.toString)
    }
  }

  override def withRequest[A](correlationId: String = UUID.randomUUID().toString)(implicit fa: IO[A]): IO[A] =
    for {
      _ <- ctxOp.setRequest(UUID.randomUUID().toString)
      _ <- ctxOp.setCorrelation(correlationId)
      start <- Clock[IO].monotonic
      _ <- ctxOp.markStart(start.toMillis)
      result <- fa.guaranteeCase {
        case Outcome.Succeeded(_) =>
          for {
            end <- Clock[IO].monotonic
            _ <- ctxOp.markEnd(end.toMillis)
            _ <- info("Request Completed")
          } yield ()
        case Outcome.Errored(e) =>
          for {
            end <- Clock[IO].monotonic
            _ <- ctxOp.markEnd(end.toMillis)
            _ <- error("RequestFailed", e)
          } yield ()
        case Outcome.Canceled() =>
          for {
            end <- Clock[IO].monotonic
            _ <- ctxOp.markEnd(end.toMillis)
            _ <- warn("Request cancelled")
          } yield ()
      }
    } yield result

  override def updateValues(key: String, value: String): IO[Unit] = ctxOp.updateValues(key, value)

  override def setCorrelationId(id: String): IO[Unit] = ctxOp.setCorrelation(id)

  override def setRequestId(id: String): IO[Unit] = ctxOp.setRequest(id)

  override def getIOStorage: IO[IOStorage] = ctxOp.get
}
