package logger

import cats.effect.Clock
import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.LiftIO
import cats.effect.kernel.Outcome
import contextStorage.ContextOperations
import contextStorage.IOStorage
import java.util.UUID
import logEvent.LogEvent
import logEvent.LogLevel
import logEvent.LogLevel._
import logSink.LogSink

trait BridgeLogger {
  def trace(msg: String): IO[Unit]
  def trace(msg: String, values: Map[String, String]): IO[Unit]
  def debug(msg: String): IO[Unit]
  def debug(msg: String, values: Map[String, String]): IO[Unit]
  def info(msg: String): IO[Unit]
  def info(msg: String, values: Map[String, String]): IO[Unit]
  def warn(msg: String): IO[Unit]
  def warn(msg: String, values: Map[String, String]): IO[Unit]
  def error(msg: String): IO[Unit]
  def error(msg: String, values: Map[String, String]): IO[Unit]
  def error(msg: String, e: Throwable): IO[Unit]
  def error(msg: String, e: Throwable, values: Map[String, String]): IO[Unit]
  def withRequest[A](
      values: Map[String, String] = Map[String, String](),
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A]): IO[A]
  def updateValues(key: String, value: String): IO[Unit]
  def setCorrelationId(id: String): IO[Unit]
  def setRequestId(id: String): IO[Unit]
}

final class BridgeLoggerImpl(ioStorage: IOLocal[IOStorage], sink: LogSink) extends BridgeLogger {
  private val contextOps: ContextOperations = new ContextOperations(ioStorage)

  private def toEvent(
      message: String,
      level: LogLevel,
      e: Option[Throwable] = None,
  ): IO[LogEvent] = {
    for {
      now <- Clock[IO].realTime
      storage <- ioStorage.get
      event = LogEvent(
        level = level,
        message = message,
        timestamp = now.toMillis,
        context = storage,
        throwable = e,
      )
    } yield event
  }

  override def trace(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Trace)
      _ <- sink.trace(event)
    } yield ()
  }

  override def trace(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- trace(msg)
    } yield ()
  }

  override def debug(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Debug)
      _ <- sink.debug(event)
    } yield ()
  }

  override def debug(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- debug(msg)
    } yield ()
  }

  override def info(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Info)
      _ <- sink.info(event)
    } yield ()
  }

  override def info(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- info(msg)
    } yield ()
  }

  override def warn(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Warn)
      _ <- sink.warn(event)
    } yield ()
  }

  override def warn(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- warn(msg)
    } yield ()
  }

  override def error(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Error)
      _ <- sink.error(event)
    } yield ()
  }

  override def error(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- error(msg)
    } yield ()
  }

  override def error(msg: String, e: Throwable): IO[Unit] = {
    for {
      event <- toEvent(msg, Error, Some(e))
      _ <- sink.error(event)
    } yield ()
  }

  override def error(msg: String, e: Throwable, values: Map[String, String]): IO[Unit] = {
    for {
      storage <- ioStorage.get
      _ <- ioStorage.set(storage.copy(values = values))
      _ <- error(msg, e)
    } yield ()
  }

  override def withRequest[A](
      values: Map[String, String] = Map(),
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A]): IO[A] = {
    val storage = IOStorage.empty
    val updated =
      storage.copy(requestId = requestId, correlationId = correlationId, values = values)
    withRequestInternal(updated)(fa)
  }

  private def withRequestInternal[A](ioStorageIn: IOStorage)(fa: IO[A]): IO[A] = {
    val contextSetup = for {
      _ <- contextOps.setRequest(ioStorageIn.requestId)
      _ <- contextOps.setCorrelation(ioStorageIn.correlationId)
      start <- Clock[IO].realTime
      _ <- contextOps.markStart(start.toMillis)
    } yield ()
    val faGuarantee = for {
      result <- fa.guaranteeCase {
        case Outcome.Succeeded(_) =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- info("Request Completed").handleErrorWith(_ => IO())
          } yield ()
        case Outcome.Errored(e) =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- error("Request failed with exception", e).handleErrorWith(_ => IO())
          } yield ()
        case Outcome.Canceled() =>
          for {
            end <- Clock[IO].realTime
            _ <- contextOps.markEnd(end.toMillis)
            _ <- warn("Request cancelled").handleErrorWith(_ => IO())
          } yield ()
      }
    } yield result
    for {
      oldIOStorage <- ioStorage.get
      results <- (ioStorage.set(IOStorage.empty) >> contextSetup >> faGuarantee)
        .guarantee(ioStorage.set(oldIOStorage))
    } yield results
  }

  override def updateValues(key: String, value: String): IO[Unit] =
    contextOps.updateValues(key, value)

  override def setCorrelationId(id: String): IO[Unit] = contextOps.setCorrelation(id)

  override def setRequestId(id: String): IO[Unit] = contextOps.setRequest(id)

  protected def getIOStorage: IO[IOStorage] = contextOps.get

}

object BridgeLogger {
  def lift[F[_]: LiftIO](logger: BridgeLogger): GenericBridgeLogger[F] = {
    GenericBridgeLogger.fromBridge[F](logger)
  }
}
