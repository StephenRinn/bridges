/*
 * Copyright 2026 Stephen Rinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import logEvent.LogField
import logEvent.LogLevel
import logEvent.LogLevel._
import logEvent.LogValue
import logSink.LogSink
import logger.traceContext.TraceContextProvider
import scala.math.Ordered.orderingToOrdered
import scala.util.Random

trait BridgeLogger {
  val bridgeConfig: BridgeLoggerConfig
  def trace(msg: String, fields: LogField*): IO[Unit]
  def traceUpdateContext(msg: String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def debug(msg: String, fields: LogField*): IO[Unit]
  def debugUpdateContext(msg: String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def info(msg: String, fields: LogField*): IO[Unit]
  def infoUpdateContext(msg: String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def warn(msg: String, fields: LogField*): IO[Unit]
  def warnUpdateContext(msg: String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def error(msg: String, fields: LogField*): IO[Unit]
  def errorUpdateContext(msg: String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def error(msg: String, e: Throwable, fields: LogField*): IO[Unit]
  def errorUpdateContext(
      msg: String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit]
  def withRequest[A](
      values: Map[String, LogValue] = Map[String, LogValue](),
      sampleRequest: Option[Boolean] = None,
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A])(implicit config: Option[BridgeLoggerConfig] = None): IO[A]
  def updateValues(key: String, value: LogValue): IO[Unit]
  def setCorrelationId(id: String): IO[Unit]
  def setRequestId(id: String): IO[Unit]
  protected[logger] def log(
      level: LogLevel,
      message: String,
      fields: Seq[LogField],
      throwable: Option[Throwable] = None,
      config: Option[BridgeLoggerConfig] = None,
  ): IO[Unit]
  def withConfig(
      minLevel: Option[LogLevel] = None,
      replayAllLogLevel: Option[LogLevel] = None,
      duplicateEntriesOnBufferDump: Option[Boolean] = None,
      sampleRate: Option[Float] = None,
      sampleBelowMinLevel: Option[Boolean] = None,
      bufferBelowMinLevel: Option[Boolean] = None,
      bufferSize: Option[Int] = None,
  ): IO[Unit]
}

final class BridgeLoggerImpl private[logger] (
    ioStorage: IOLocal[IOStorage],
    traceContextProvider: TraceContextProvider = TraceContextProvider.noop,
    sink: LogSink,
    bridgeLoggerConfig: BridgeLoggerConfig = BridgeLoggerConfig.default,
) extends BridgeLogger {
  override val bridgeConfig: BridgeLoggerConfig = bridgeLoggerConfig
  private val contextOps: ContextOperations =
    new ContextOperations(ioStorage, bridgeLoggerConfig.bufferSize)

  private def toEvent(
      message: String,
      level: LogLevel,
      storage0: Option[IOStorage] = None,
      e: Option[Throwable] = None,
      values: Seq[LogField] = Seq[LogField]().empty,
  ): IO[(LogEvent, IOStorage)] = {
    for {
      now <- Clock[IO].realTime
      storage <-
        if (storage0.isDefined) {
          IO.pure(storage0.get)
        } else { contextOps.get }
      attributes <- traceContextProvider.attributes
      event = LogEvent(
        level = level,
        message = message,
        timestamp = now.toMillis,
        context = storage,
        attributes = attributes,
        throwable = e,
        logContext = values.iterator.map(field => field.key -> field.value()).toMap,
      )
    } yield (event, storage)
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

  private def sampleEligible(storage: IOStorage, config: BridgeLoggerConfig): IO[Boolean] = {
    for {
      sample <-
        if (storage.sampled.isEmpty) {
          val sampled = Random.between(0.0f, 1.0f) < config.sampleRate
          for {
            _ <- contextOps.setSampled(sampled)
          } yield sampled
        } else IO(storage.sampled.get)
    } yield sample
  }

  private def evaluateToEvent(
      level: LogLevel,
      ioStorage: IOStorage,
      singleLogConfig: Option[BridgeLoggerConfig] = None,
  ): Boolean = {
    val config = (singleLogConfig, ioStorage.config) match {
      case (Some(logConfig), _) => logConfig
      case (_, Some(ioLocalConfig)) => ioLocalConfig
      case _ => bridgeLoggerConfig
    }
    val sampleCheck = ioStorage.sampled.contains(true) || ioStorage.sampled.isEmpty
    level match {
      case l if l >= config.minLevel => true
      case l if l < config.minLevel && config.bufferBelowMinLevel => true
      case l if l < config.minLevel && config.sampleBelowMinLevel =>
        if (sampleCheck) {
          true
        } else false
      case _ => false
    }
  }

  protected[logger] def log(
      level: LogLevel,
      message: String,
      fields: Seq[LogField],
      throwable: Option[Throwable] = None,
      singleLogConfig: Option[BridgeLoggerConfig] = None,
  ): IO[Unit] = {
    for {
      storage <- contextOps.get
      passThrough = evaluateToEvent(level, storage, singleLogConfig)

      _ <-
        if (!passThrough) { IO.unit }
        else {
          for {
            event <- toEvent(
              message = message,
              level = level,
              storage0 = Some(storage),
              e = throwable,
              values = fields,
            )
            _ <- evaluateLog(
              param = event._1,
              storage = storage,
              fa = sink.log,
              singleLogConfig = singleLogConfig,
            )
          } yield ()
        }
    } yield ()
  }

  private def emitEligible(
      param: LogEvent,
      sampled: Boolean,
      config: BridgeLoggerConfig,
  ): Boolean = {
    (param.level > config.minLevel
    || (sampled && (config.sampleBelowMinLevel || config.minLevel == param.level)))
  }

  private def bufferDumpEligible(param: LogEvent, config: BridgeLoggerConfig): Boolean = {
    param.level >= config.replayAllLogLevel
  }

  private def bufferEligible(
      param: LogEvent,
      config: BridgeLoggerConfig,
      sampled: Boolean,
  ): Boolean = {
    ((param.level < config.minLevel && config.bufferBelowMinLevel)
    || (param.level >= config.minLevel && config.duplicateEntriesOnBufferDump)
    || (param.level == config.minLevel && !sampled))
  }

  private def evaluateLog(
      param: LogEvent,
      storage: IOStorage,
      fa: LogEvent => IO[Unit],
      singleLogConfig: Option[BridgeLoggerConfig] = None,
  ): IO[Unit] = {
    val config = (singleLogConfig, storage.config) match {
      case (Some(logConfig), _) => logConfig
      case (_, Some(ioLocalConfig)) => ioLocalConfig
      case _ => bridgeLoggerConfig
    }
    for {
      sampled <- sampleEligible(storage, config)
      bufferDump = bufferDumpEligible(param, config)
      emit = emitEligible(param, sampled, config)
      buffer = bufferEligible(param, config, sampled)
      _ <- (bufferDump, emit, buffer) match {
        case (true, _, _) => rebuildAndPrint(param, storage, fa)
        case (_, true, _) =>
          for {
            _ <-
              if (config.duplicateEntriesOnBufferDump && buffer) {
                contextOps.updateRebuildLog(param, param.level)
              } else IO.unit
            _ <- fa(param)
          } yield ()
        case (_, _, true) => contextOps.updateRebuildLog(param, param.level)
        case _ => IO.unit
      }
    } yield ()
  }

  private def rebuildRouter(rebuildLogs: List[RebuildLog]): List[IO[Unit]] = {
    rebuildLogs.map { rebuildLog =>
      sink.log(rebuildLog.log)
    }
  }

  override def trace(msg: String, fields: LogField*): IO[Unit] = {
    log(Trace, msg, fields)
  }

  /** Values are added to the context, not based on this log event only
    */
  override def traceUpdateContext(
      msg: String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- trace(msg = msg, fields = fields: _*)
    } yield ()
  }

  override def debug(
      msg: String,
      fields: LogField*,
  ): IO[Unit] = {
    log(level = Debug, message = msg, fields = fields)
  }

  /** Values are added to the context, not based on this log event only
    */
  override def debugUpdateContext(
      msg: String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- debug(msg, fields: _*)
    } yield ()
  }

  override def info(
      msg: String,
      values: LogField*,
  ): IO[Unit] = {
    log(level = Info, message = msg, fields = values)
  }

  /** Values are added to the context, not based on this log event only
    */
  override def infoUpdateContext(
      msg: String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- info(msg, fields: _*)
    } yield ()
  }

  override def warn(msg: String, fields: LogField*): IO[Unit] = {
    log(level = Warn, message = msg, fields = fields)
  }

  /** Values are added to the context, not based on this log event only
    */
  override def warnUpdateContext(
      msg: String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- warn(msg, fields: _*)
    } yield ()
  }

  override def error(msg: String, fields: LogField*): IO[Unit] = {
    log(level = Error, message = msg, fields = fields)
  }

  /** Values are added to the context, not based on this log event only
    */
  override def errorUpdateContext(
      msg: String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- error(msg, fields: _*)
    } yield ()
  }

  override def error(msg: String, e: Throwable, fields: LogField*): IO[Unit] = {
    log(level = Error, message = msg, fields = fields, throwable = Some(e))
  }

  /** Values are added to the context, not based on this log event only
    */
  override def errorUpdateContext(
      msg: String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    for {
      _ <- contextOps.updateValues(values)
      _ <- error(msg, e, fields: _*)
    } yield ()
  }

  override def withRequest[A](
      values: Map[String, LogValue] = Map(),
      sampleRequest: Option[Boolean] = None,
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A])(implicit config: Option[BridgeLoggerConfig] = None): IO[A] = {
    val storage = IOStorage.empty
    val updated =
      storage.copy(
        requestId = requestId,
        correlationId = correlationId,
        values = values,
        sampled = sampleRequest,
        config = config,
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

  override def updateValues(key: String, value: LogValue): IO[Unit] =
    contextOps.updateValue(key, value)

  override def setCorrelationId(id: String): IO[Unit] = contextOps.setCorrelation(id)

  override def setRequestId(id: String): IO[Unit] = contextOps.setRequest(id)

  override def withConfig(
      minLevel: Option[LogLevel] = None,
      replayAllLogLevel: Option[LogLevel] = None,
      duplicateEntriesOnBufferDump: Option[Boolean] = None,
      sampleRate: Option[Float] = None,
      sampleBelowMinLevel: Option[Boolean] = None,
      bufferBelowMinLevel: Option[Boolean] = None,
      bufferSize: Option[Int] = None,
  ): IO[Unit] = {
    for {
      storage <- contextOps.get
      config = storage.config match {
        case Some(value) =>
          value.copy(
            minLevel = minLevel.getOrElse(value.minLevel),
            replayAllLogLevel = replayAllLogLevel.getOrElse(value.replayAllLogLevel),
            duplicateEntriesOnBufferDump =
              duplicateEntriesOnBufferDump.getOrElse(value.duplicateEntriesOnBufferDump),
            sampleRate = sampleRate.getOrElse(value.sampleRate),
            sampleBelowMinLevel = sampleBelowMinLevel.getOrElse(value.sampleBelowMinLevel),
            bufferBelowMinLevel = bufferBelowMinLevel.getOrElse(value.bufferBelowMinLevel),
            bufferSize = bufferSize.getOrElse(value.bufferSize),
          )
        case None =>
          bridgeLoggerConfig.copy(
            minLevel = minLevel.getOrElse(bridgeLoggerConfig.minLevel),
            replayAllLogLevel = replayAllLogLevel.getOrElse(bridgeLoggerConfig.replayAllLogLevel),
            duplicateEntriesOnBufferDump =
              duplicateEntriesOnBufferDump.getOrElse(bridgeLoggerConfig.duplicateEntriesOnBufferDump),
            sampleRate = sampleRate.getOrElse(bridgeLoggerConfig.sampleRate),
            sampleBelowMinLevel = sampleBelowMinLevel.getOrElse(bridgeLoggerConfig.sampleBelowMinLevel),
            bufferBelowMinLevel = bufferBelowMinLevel.getOrElse(bridgeLoggerConfig.bufferBelowMinLevel),
            bufferSize = bufferSize.getOrElse(bridgeLoggerConfig.bufferSize),
          )
      }
      _ <- contextOps.updateConfig(config)
    } yield ()
  }

  private def getStorage: IO[IOStorage] = {
    ioStorage.get
  }
}

object BridgeLogger {
  def lift[F[_]: LiftIO](logger: BridgeLogger): GenericBridgeLogger[F] = {
    GenericBridgeLogger.fromBridge[F](logger)
  }

  case class builder(
      traceContextProvider: TraceContextProvider = TraceContextProvider.noop,
      minLevel: LogLevel = Info,
      replayAllLogLevel: LogLevel = Warn,
      duplicateEntriesOnBufferDump: Boolean = false,
      sampleRate: Float = 1.0f,
      sampleIncludesBelowMinLevel: Boolean = false,
      bufferMessagesBelowMinLevel: Boolean = false,
      logBufferSize: Int = 200,
  ) {

    /** Minimum level used as the sampling/buffering boundary.
      *
      * Logs above this level are always emitted.
      *
      * Logs at this level are emitted only when the request is sampled.
      *
      * Logs below this level are emitted only when the request is sampled and sampleBelowMinLevel
      * is enabled.
      *
      * When buffering is enabled, logs at or below this level are retained so they can be replayed
      * when a log reaches replayAllLogLevel.
      *
      * @param logLevel
      *   Minimum level boundary for sampling and buffering.
      */
    def withMinLevel(logLevel: LogLevel): builder = {
      copy(minLevel = logLevel)
    }

    /** Determines the percentage of requests that are sampled.
      *
      * Sampling is evaluated once per request and stored in the request context. When a request is
      * sampled, logs at the minimum level may be emitted and, when enabled, logs below the minimum
      * level may also be emitted.
      *
      * @param sampleRate
      *   Fraction of requests to sample, from 0.0 to 1.0.
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

    /** Set whether an emitted log is also stored in the buffer to condense all logs and more easily
      * see order etc. Defaults to false
      */
    def duplicateEntriesOnBufferDump(duplicate: Boolean): builder = {
      copy(duplicateEntriesOnBufferDump = duplicate)
    }

    /** This is a customizable level for what causes a buffer replay. If a log meets or exceeds this
      * level all logs will be replayed.
      */
    def replayAllLogLevel(replayAllLogLevel: LogLevel): builder = {
      copy(replayAllLogLevel = replayAllLogLevel)
    }

    def traceContextProvider(traceContextProvider: TraceContextProvider): builder = {
      copy(traceContextProvider = traceContextProvider)
    }

    private def toBridgeLoggerConfig: BridgeLoggerConfig = {
      new BridgeLoggerConfig(
        minLevel = this.minLevel,
        replayAllLogLevel = this.replayAllLogLevel,
        duplicateEntriesOnBufferDump = this.duplicateEntriesOnBufferDump,
        sampleRate = this.sampleRate,
        sampleBelowMinLevel = this.sampleIncludesBelowMinLevel,
        bufferBelowMinLevel = this.bufferMessagesBelowMinLevel,
        bufferSize = this.logBufferSize,
      )
    }

    def build(sink: LogSink): IO[BridgeLogger] = {
      IOLocal(IOStorage.empty).map { ioStorage =>
        new BridgeLoggerImpl(
          traceContextProvider = this.traceContextProvider,
          ioStorage = ioStorage,
          sink = sink,
          bridgeLoggerConfig = toBridgeLoggerConfig,
        )
      }
    }
  }
}
