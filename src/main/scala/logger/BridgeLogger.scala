package logger

import cats.effect.kernel.Outcome
import cats.effect.{Clock, IO, IOLocal}
import contextStorage.{ContextOperations, IOStorage}
import java.util.UUID
import logEvent.{LogEvent, LogLevel}
import logEvent.LogLevel._
import logSink.LogSink

trait BridgeLogger {
  def trace(msg: String): IO[Unit]
  def debug(msg: String): IO[Unit]
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
  def withRequest[A](correlationId: String = UUID.randomUUID().toString, fa: IO[A]): IO[A]
  def updateValues(key: String, value: String): IO[Unit]
  def setCorrelationId(id: String): IO[Unit]
  def setRequestId(id: String): IO[Unit]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private val contextOps: ContextOperations = new ContextOperations(ioStorage)

  private def toEvent(message: String, level: LogLevel, e: Option[Throwable] = None): IO[LogEvent] = {
    for {
      now <- Clock[IO].realTime
      storage <- ioStorage.get
      event = LogEvent(level = level, message = message, timestamp = now.toMillis, context = storage, throwable = e)
    } yield event
  }

  override def trace(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Trace)
      _ <- sink.trace(event)
    } yield ()
  }

  override def debug(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Debug)
      _ <- sink.debug(event)
    } yield ()
  }

  override def info(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Info)
      _ <- sink.info(event)
    } yield ()
  }

  override def warn(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Warn)
      _ <- sink.warn(event)
    } yield ()
  }

  override def error(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Error)
      _ <- sink.error(event)
    } yield ()
  }

  override def error(msg: String, e: Throwable): IO[Unit] = {
    for {
      event <- toEvent(msg, Error)
      _ <- sink.error(event)
    } yield ()
  }

  override def withRequest[A](correlationId: String = UUID.randomUUID().toString, fa: IO[A]): IO[A] =
    for {
      oldIOStorage <- ioStorage.get
      _ <- ioStorage.set(IOStorage.empty)
      _ <- contextOps.setRequest(UUID.randomUUID().toString)
      _ <- contextOps.setCorrelation(correlationId)
      start <- Clock[IO].realTime
      _ <- contextOps.markStart(start.toMillis)
      result <- fa.guaranteeCase {
        case Outcome.Succeeded(_) =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- info("Request Completed")
            _ <- ioStorage.set(oldIOStorage)
          } yield ()
        case Outcome.Errored(e) =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- error("Request failed with exception", e)
            _ <- ioStorage.set(oldIOStorage)
          } yield ()
        case Outcome.Canceled() =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- warn("Request cancelled")
            _ <- ioStorage.set(oldIOStorage)
          } yield ()
      }
    } yield result

  override def updateValues(key: String, value: String): IO[Unit] = contextOps.updateValues(key, value)

  override def setCorrelationId(id: String): IO[Unit] = contextOps.setCorrelation(id)

  override def setRequestId(id: String): IO[Unit] = contextOps.setRequest(id)

  protected def getIOStorage: IO[IOStorage] = contextOps.get
}
