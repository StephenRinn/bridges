package logger

import cats.effect.Clock
import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.LiftIO
import cats.effect.kernel.Outcome
import contextStorage._
import java.util.UUID
import logEvent.LogEvent
import logEvent.LogLevel
import logEvent.LogLevel._
import logSink.LogSink
import scala.math.Ordered.orderingToOrdered
import scala.util.Random

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

final class BridgeLoggerImpl(
    ioStorage: IOLocal[IOStorage],
    sink: LogSink,
    bridgeLoggerConfig: BridgeLoggerConfig = BridgeLoggerConfig.default,
) extends BridgeLogger {
  private val contextOps: ContextOperations = new ContextOperations(ioStorage, bridgeLoggerConfig.bufferSize)

  private def toEvent(
      message: String,
      level: LogLevel,
      e: Option[Throwable] = None,
  ): IO[LogEvent] = {
    for {
      now <- Clock[IO].realTime
      storage <- contextOps.get
      event = LogEvent(
        level = level,
        message = message,
        timestamp = now.toMillis,
        context = storage,
        throwable = e,
      )
    } yield event
  }

  private def rebuildAndPrint(
      param: LogEvent,
      storage: IOStorage,
      fa: LogEvent => IO[Unit],
  ): IO[Unit] = {
    val rebuildList = storage.rebuildLog
    val ioList = rebuildRouter(rebuildList)
    for {
      _ <- ioList.sequence_
      _ <- contextOps.clearRebuildLogs
      _ <- fa(param)
    } yield ()
  }

  private def shouldBuffer(
      param: LogEvent,
      fa: LogEvent => IO[Unit],
  ): IO[Unit] = {
    contextOps.get.flatMap { storage =>
      val isSampled = storage.sampled.contains(true)
      val meetsMinLevel = bridgeLoggerConfig.minLevel <= param.level
      // Handle special case of error first
      if (param.level == Error) {
        for {
          _ <- rebuildAndPrint(param, storage, fa)
          _ <- contextOps.setSampled(true)
        } yield ()
        // Check for sampled and if we log all levels for samples
      } else if (isSampled && (bridgeLoggerConfig.sampleBelowMinLevel || meetsMinLevel)){
        fa(param)
        // If we haven't checked sampling yet do so if the log is at least the minimum level
      } else if (storage.sampled.isEmpty && meetsMinLevel) {
        val sampled = bridgeLoggerConfig.sampleRate > Random.between(0f, 1f)
        contextOps.setSampled(sampled).flatMap { _ =>
          if (sampled) {
            rebuildAndPrint(param, storage, fa)
          } else IO.unit
        }
        // Check if sampled or if it's just above min level
      } else if (isSampled || meetsMinLevel) {
        storage.rebuildLog match {
          case Nil => fa(param)
          case _ => rebuildAndPrint(param, storage, fa)
        }
        // Default case
      } else {
        if (!meetsMinLevel && bridgeLoggerConfig.bufferBelowMinLevel) {
          contextOps.updateRebuildLog(param, param.level)
        } else { IO.unit }
      }
    }
  }

  private def rebuildRouter(rebuildLogs: List[RebuildLog]): List[IO[Unit]] = {
    rebuildLogs.map { rebuildLog =>
      rebuildLog.level match {
        case LogLevel.Trace => sink.trace(rebuildLog.log)
        case LogLevel.Debug => sink.debug(rebuildLog.log)
        case LogLevel.Info => sink.info(rebuildLog.log)
        case LogLevel.Warn => sink.warn(rebuildLog.log)
        case LogLevel.Error => sink.error(rebuildLog.log)
      }
    }
  }

  override def trace(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Trace)
      _ <- shouldBuffer(event, sink.trace)
    } yield ()
  }

  override def trace(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- trace(msg)
    } yield ()
  }

  override def debug(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Debug)
      _ <- shouldBuffer(event, sink.debug)
    } yield ()
  }

  override def debug(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- debug(msg)
    } yield ()
  }

  override def info(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Info)
      _ <- shouldBuffer(event, sink.info)
    } yield ()
  }

  override def info(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- info(msg)
    } yield ()
  }

  override def warn(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Warn)
      _ <- shouldBuffer(event, sink.warn)
    } yield ()
  }

  override def warn(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- warn(msg)
    } yield ()
  }

  override def error(msg: String): IO[Unit] = {
    for {
      event <- toEvent(msg, Error)
      _ <- shouldBuffer(event, sink.error)
    } yield ()
  }

  override def error(msg: String, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- error(msg)
    } yield ()
  }

  override def error(msg: String, e: Throwable): IO[Unit] = {
    for {
      event <- toEvent(msg, Error, Some(e))
      _ <- shouldBuffer(event, sink.error)
    } yield ()
  }

  override def error(msg: String, e: Throwable, values: Map[String, String]): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
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

  private def withRequestInternal[A](newStorage: IOStorage)(fa: IO[A]): IO[A] = {
    val contextSetup = for {
      _ <- ioStorage.set(newStorage)
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
      oldIOStorage <- contextOps.get
      results <- (ioStorage.set(IOStorage.empty) >> contextSetup >> faGuarantee)
        .guarantee(ioStorage.set(oldIOStorage))
    } yield results
  }

  override def updateValues(key: String, value: String): IO[Unit] =
    contextOps.updateValues(key, value)

  override def setCorrelationId(id: String): IO[Unit] = contextOps.setCorrelation(id)

  override def setRequestId(id: String): IO[Unit] = contextOps.setRequest(id)

  def getStorage: IO[IOStorage] = ioStorage.get
}

object BridgeLogger {
  def lift[F[_]: LiftIO](logger: BridgeLogger): GenericBridgeLogger[F] = {
    GenericBridgeLogger.fromBridge[F](logger)
  }
}
