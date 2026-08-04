/*
 * /*
 *  * Copyright 2026 Stephen Rinn
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  */
 */

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
      sampleRequest: Option[Boolean] = None,
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
  private val contextOps: ContextOperations =
    new ContextOperations(ioStorage, bridgeLoggerConfig.bufferSize)

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

  private def sampleEligible: IO[Boolean] = {
    for {
      storage <- contextOps.get
      sample <-
        if (storage.sampled.isEmpty) {
          val sampled = Random.between(0.0f, 1.0f) < bridgeLoggerConfig.sampleRate
          for {
            _ <- contextOps.setSampled(sampled)
          } yield sampled
        } else IO(storage.sampled.get)
    } yield sample

  }

  private def emitEligible(param: LogEvent, sampled: Boolean): Boolean = {
    (param.level > bridgeLoggerConfig.minLevel
    || (sampled && (bridgeLoggerConfig.sampleBelowMinLevel || bridgeLoggerConfig.minLevel == param.level)))
  }

  private def bufferDumpEligible(param: LogEvent): Boolean = {
    param.level >= bridgeLoggerConfig.replayAllLogLevel
  }

  private def bufferEligible(param: LogEvent): Boolean = {
    param.level <= bridgeLoggerConfig.minLevel && bridgeLoggerConfig.bufferBelowMinLevel
  }

  private def evaluateLog(param: LogEvent, fa: LogEvent => IO[Unit]): IO[Unit] = {
    for {
      storage <- contextOps.get
      sampled <- sampleEligible
      bufferDump = bufferDumpEligible(param)
      emit = emitEligible(param, sampled)
      buffer = bufferEligible(param)
      _ <- (bufferDump, emit, buffer) match {
        case (true, _, _) => rebuildAndPrint(param, storage, fa)
        case (_, true, false) =>
          for {
            _ <- contextOps.updateRebuildLog(param, param.level)
            _ <- fa(param)
          } yield ()
        case (_, _, true) => contextOps.updateRebuildLog(param, param.level)
        case _ => IO.unit
      }
    } yield ()
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
      _ <- evaluateLog(event, sink.trace)
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
      _ <- evaluateLog(event, sink.debug)
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
      _ <- evaluateLog(event, sink.info)
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
      _ <- evaluateLog(event, sink.warn)
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
      _ <- evaluateLog(event, sink.error)
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
      _ <- evaluateLog(event, sink.error)
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
      sampleRequest: Option[Boolean] = None,
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A]): IO[A] = {
    val storage = IOStorage.empty
    val updated =
      storage.copy(
        requestId = requestId,
        correlationId = correlationId,
        values = values,
        sampled = sampleRequest,
      )
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

  case class builder(
      minLevel: LogLevel = Info,
      replayAllLogsLevel: LogLevel = Warn,
      sampleRate: Float = 1.0f,
      sampleIncludesBelowMinLevel: Boolean = false,
      bufferMessagesBelowMinLevel: Boolean = false,
      logBufferSize: Int = 200,
  ) {

    /** Custom minimum level which will cause log to emit. If a log does not emit the message will
      * be stored in a buffer to be replayed if a higher level log is later triggered.
      *
      * This is also the level which sampleRate affects so a min level of Info will only emit if it
      * is sampled. Sampling defaults to 1.0 for this reason.
      *
      * @param logLevel
      *   Minimum level for log to emit
      */
    def withMinLevel(logLevel: LogLevel): builder = {
      copy(minLevel = logLevel)
    }

    /** Allows a sample of what otherwise may be successful calls to be emitted. Errors will always
      * emit regardless of minimum level.
      *
      * @param sampleRate
      *   percent of logs to emit at minLevel
      * @return
      */
    def sampleRate(sampleRate: Float): builder = {
      copy(sampleRate = sampleRate)
    }

    /** Determines if a message with a lower level than the minimum should be buffered or ignored.
      */
    def sampleBelowMinLevel(sampleBelowMinLevel: Boolean): builder = {
      copy(sampleIncludesBelowMinLevel = sampleBelowMinLevel)
    }

    /** This determines if log levels below the minimum are buffered
     */
    def bufferBelowMinLevel(bufferBelowMinLevel: Boolean): builder = {
      copy(bufferMessagesBelowMinLevel = bufferBelowMinLevel)
    }
    private def toBridgeLoggerConfig: BridgeLoggerConfig = {
      new BridgeLoggerConfig(
        minLevel = this.minLevel,
        replayAllLogLevel = this.replayAllLogsLevel,
        sampleRate = this.sampleRate,
        sampleBelowMinLevel = this.sampleIncludesBelowMinLevel,
        bufferBelowMinLevel = this.bufferMessagesBelowMinLevel,
        bufferSize = this.logBufferSize,
      )
    }
    def build(ioStorage: IOLocal[IOStorage], sink: LogSink): BridgeLogger =
      new BridgeLoggerImpl(
        ioStorage = ioStorage,
        sink = sink,
        bridgeLoggerConfig = toBridgeLoggerConfig,
      )
  }
}
