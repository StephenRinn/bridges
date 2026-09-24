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

import cats.data.Chain
import cats.effect.Clock
import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.LiftIO
import cats.effect.kernel.Outcome
import cats.implicits.toFoldableOps
import contextStorage._
import java.util.UUID
import logEvent.LogEvent
import logEvent.LogField
import logEvent.LogLevel
import logEvent.LogLevel._
import logEvent.LogValue
import logSink.LogSink
import logger.config.BridgeLoggerConfig
import logger.config.FallbackResponse
import logger.traceContext.TraceContextProvider

trait BridgeLogger {
  def trace(msg: => String): IO[Unit]
  def trace(msg: => String, field: LogField): IO[Unit]
  def trace(msg: => String, fields: LogField*): IO[Unit]
  def traceUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def debug(msg: => String): IO[Unit]
  def debug(msg: => String, field: LogField): IO[Unit]
  def debug(msg: => String, fields: LogField*): IO[Unit]
  def debugUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def info(msg: => String): IO[Unit]
  def info(msg: => String, field: LogField): IO[Unit]
  def info(msg: => String, fields: LogField*): IO[Unit]
  def infoUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def warn(msg: => String): IO[Unit]
  def warn(msg: => String, field: LogField): IO[Unit]
  def warn(msg: => String, fields: LogField*): IO[Unit]
  def warnUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def error(msg: => String): IO[Unit]
  def error(msg: => String, field: LogField): IO[Unit]
  def error(msg: => String, fields: LogField*): IO[Unit]
  def errorUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): IO[Unit]
  def error(msg: => String, e: Throwable): IO[Unit]
  def error(msg: => String, e: Throwable, field: LogField): IO[Unit]
  def error(msg: => String, e: Throwable, fields: LogField*): IO[Unit]
  def errorUpdateContext(
      msg: => String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit]
  def withRequest[A](
      sampleRequest: Option[Boolean] = None,
      correlationId: Option[String] = None,
      requestId: Option[String] = None,
      composable: Boolean = true,
  )(fa: IO[A])(fields: LogField*)(implicit config: Option[BridgeLoggerConfig] = None): IO[A]
  def updateValues(key: String, value: LogValue): IO[Unit]
  def setCorrelationId(id: String): IO[Unit]
  def setRequestId(id: String): IO[Unit]
  protected[logger] def log(
      level: LogLevel,
      message: => String,
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
    fallbackResponse: FallbackResponse = FallbackResponse.noop,
) extends BridgeLogger {
  private val contextOps: ContextOperations =
    new ContextOperations(ioStorage, bridgeLoggerConfig.bufferSize)

  private val hasCustomFallback: Boolean = fallbackResponse ne FallbackResponse.noop

  private val hasTraceContext = traceContextProvider ne TraceContextProvider.noop

  private val minLevelShortCircuit =
    !bridgeLoggerConfig.bufferBelowMinLevel && !bridgeLoggerConfig.sampleBelowMinLevel

  private val emptyFields: Seq[LogField] = Nil

  private val pureEmptyStorage: IO[IOStorage] = IO.pure(IOStorage.empty)

  private def fieldsToMap(fields: Seq[LogField]): Map[String, LogValue] = {
    fields.length match {
      case 0 => Map.empty
      case 1 =>
        val f = fields.head
        Map(f.key -> f.value())
      case 2 =>
        val f0 = fields.head
        val f1 = fields(1)
        Map(f0.key -> f0.value(), f1.key -> f1.value())
      case n =>
        val builder = Map.newBuilder[String, LogValue]
        builder.sizeHint(n)
        fields.foreach(f => builder += (f.key -> f.value()))
        builder.result()
    }
  }

  private def toEvent(
      message: String,
      level: LogLevel,
      storage: IOStorage,
      e: Option[Throwable] = None,
      values: Seq[LogField] = Seq.empty,
  ): IO[LogEvent] = {
    val ctx = fieldsToMap(values)

    if (!hasTraceContext) {
      IO.delay {
        val now = System.currentTimeMillis()
        LogEvent(
          level = level,
          message = message,
          timestamp = now,
          context = storage,
          attributes = Map.empty,
          throwable = e,
          logContext = ctx,
        )
      }
    } else {
      for {
        now <- Clock[IO].realTime
        attributes <- traceContextProvider.attributes
      } yield {
        LogEvent(
          level = level,
          message = message,
          timestamp = now.toMillis,
          context = storage,
          attributes = attributes,
          throwable = e,
          logContext = ctx,
        )
      }
    }
  }

  private def rebuildAndPrint(
      param: LogEvent,
      storage: IOStorage,
      fa: LogEvent => IO[Unit],
  ): IO[Unit] = {
    rebuildRouter(storage.rebuildLog) >> contextOps.clearRebuildLogs >> fa(param)
//    val rebuildList = storage.rebuildLog
//    for {
//      _ <- rebuildRouter(rebuildList)
//      _ <- contextOps.clearRebuildLogs
//      _ <- fa(param)
//    } yield ()
  }

  private def sampleEligible(storage: IOStorage, config: BridgeLoggerConfig): IO[Boolean] = {
    storage.sampled match {
      case Some(value) => IO.pure(value)
      case None =>
        IO.delay(java.util.concurrent.ThreadLocalRandom.current().nextFloat() < config.sampleRate)
          .flatMap { sampled =>
            contextOps.setSampled(sampled).as(sampled)
          }
    }
  }

  private def resolveConfig(
      singleLogConfig: Option[BridgeLoggerConfig],
      storageConfig: Option[BridgeLoggerConfig],
  ): BridgeLoggerConfig =
    if (singleLogConfig.isDefined) singleLogConfig.get
    else if (storageConfig.isDefined) storageConfig.get
    else bridgeLoggerConfig

  private def evaluateToEvent(
      level: LogLevel,
      ioStorage: IOStorage,
      config: BridgeLoggerConfig,
  ): Boolean = {
    val lvl = level.level
    val minLvl = config.minLevel.level

    if (lvl >= minLvl) {
      true
    } else if (!config.bufferBelowMinLevel && !config.sampleBelowMinLevel) {
      false
    } else if (config.bufferBelowMinLevel) {
      true
    } else if (config.sampleBelowMinLevel) {
      ioStorage.sampled match {
        case Some(value) => value
        case None => true
      }
    } else {
      false
    }
  }

  protected[logger] def log(
      level: LogLevel,
      message: => String,
      fields: Seq[LogField],
      throwable: Option[Throwable] = None,
      singleLogConfig: Option[BridgeLoggerConfig] = None,
  ): IO[Unit] = {
    if (
      minLevelShortCircuit && singleLogConfig.isEmpty && bridgeLoggerConfig.minLevel.level > level.level
    ) {
      IO.unit
    } else {
      contextOps.get.flatMap { storage =>
        logWithStorage(level, message, fields, storage, throwable, singleLogConfig)
      }
    }
  }

  private def logWithStorage(
      level: LogLevel,
      message: => String,
      fields: Seq[LogField],
      storage: IOStorage,
      throwable: Option[Throwable] = None,
      singleLogConfig: Option[BridgeLoggerConfig] = None,
  ): IO[Unit] = {
    val config = resolveConfig(singleLogConfig, storage.config)
    if (!evaluateToEvent(level, storage, config)) {
      IO.unit
    } else {
      val evaluatedMsg = message
      val ctx = fieldsToMap(fields)
      if (!hasTraceContext) {
        val event = LogEvent(
          level = level,
          message = evaluatedMsg,
          timestamp = System.currentTimeMillis(),
          context = storage,
          attributes = Map.empty,
          throwable = throwable,
          logContext = ctx,
        )
        evaluateLog(param = event, storage = storage, fa = sink.log, config = config)
      } else {
        for {
          now <- Clock[IO].realTime
          attributes <- traceContextProvider.attributes
          event = LogEvent(
            level = level,
            message = evaluatedMsg,
            timestamp = now.toMillis,
            context = storage,
            attributes = attributes,
            throwable = throwable,
            logContext = ctx,
          )
          res <- evaluateLog(param = event, storage = storage, fa = sink.log, config = config)
        } yield res
      }
    }
  }

  private def emitEligible(
      param: LogEvent,
      sampled: Boolean,
      config: BridgeLoggerConfig,
  ): Boolean = {
    val lvl = param.level.level
    val minLvl = config.minLevel.level
    lvl > minLvl || (sampled && (config.sampleBelowMinLevel || minLvl == lvl))
  }

  private def bufferDumpEligible(param: LogEvent, config: BridgeLoggerConfig): Boolean = {
    param.level.level >= config.replayAllLogLevel.level
  }

  private def bufferEligible(
      param: LogEvent,
      config: BridgeLoggerConfig,
      sampled: Boolean,
  ): Boolean = {
    val lvl = param.level.level
    val minLvl = config.minLevel.level
    (lvl < minLvl && config.bufferBelowMinLevel) ||
    (lvl >= minLvl && config.duplicateEntriesOnBufferDump) ||
    (lvl == minLvl && !sampled)
  }

  private def evaluateLog(
      param: LogEvent,
      storage: IOStorage,
      fa: LogEvent => IO[Unit],
      config: BridgeLoggerConfig,
  ): IO[Unit] = {
    storage.sampled match {
      case Some(sampled) =>
        processEvaluatedLog(param, storage, fa, config, sampled)
      case None =>
        sampleEligible(storage, config).flatMap { sampled =>
          processEvaluatedLog(param, storage, fa, config, sampled)
        }
    }
  }

  private def processEvaluatedLog(
      param: LogEvent,
      storage: IOStorage,
      fa: LogEvent => IO[Unit],
      config: BridgeLoggerConfig,
      sampled: Boolean,
  ): IO[Unit] = {
    if (bufferDumpEligible(param, config)) {
      rebuildAndPrint(param, storage, fa)
    } else if (emitEligible(param, sampled, config)) {
      if (config.duplicateEntriesOnBufferDump && bufferEligible(param, config, sampled)) {
        contextOps.updateRebuildLog(param) >> fa(param)
      } else {
        fa(param)
      }
    } else if (bufferEligible(param, config, sampled)) {
      contextOps.updateRebuildLog(param)
    } else {
      IO.unit
    }
  }

  private def rebuildRouter(rebuildLogs: Chain[RebuildLog]): IO[Unit] = {
    rebuildLogs.traverseVoid(r => sink.log(r.log))
  }

  private def handleError(
      e: Throwable,
      msg: => String,
      values: Map[String, LogValue] = Map.empty[String, LogValue],
      fields: Seq[LogField],
  ): IO[Unit] = {
    fallbackResponse.errorFallback(e, msg, values, fields)
  }

  private def handleCancel(
      msg: => String,
      values: Map[String, LogValue] = Map.empty[String, LogValue],
      fields: Seq[LogField],
  ): IO[Unit] = {
    fallbackResponse.cancelFallback(msg, values, fields)
  }

  @inline private def guarded[A](
      io: IO[A],
      msg: => String,
      fields: Seq[LogField],
      values: Map[String, LogValue] = Map.empty[String, LogValue],
  ): IO[A] = {
    if (!hasCustomFallback) {
      io
    } else {
      io.guaranteeCase {
        case Outcome.Succeeded(_) => IO.unit
        case Outcome.Errored(e) => handleError(e = e, msg = msg, values = values, fields = fields)
        case Outcome.Canceled() => handleCancel(msg = msg, values = values, fields = fields)
      }
    }
  }

  override def trace(msg: => String): IO[Unit] =
    guarded(log(Trace, msg, emptyFields), msg, emptyFields)

  override def trace(msg: => String, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Trace, msg, fields), msg, fields)
  }

  override def trace(msg: => String, fields: LogField*): IO[Unit] =
    guarded(log(Trace, msg, fields), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def traceUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Trace, msg, fields, storage)
    } yield ()
    guarded(action, msg, fields, values)
  }

  override def debug(msg: => String): IO[Unit] = {
    guarded(log(Debug, msg, emptyFields), msg, emptyFields)
  }

  def debug(msg: => String, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Debug, msg, fields), msg, fields)
  }

  override def debug(msg: => String, fields: LogField*): IO[Unit] =
    guarded(log(Debug, msg, fields), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def debugUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Debug, msg, fields, storage)
    } yield ()
    guarded(action, msg, fields, values)
  }

  override def info(msg: => String): IO[Unit] =
    guarded(log(Info, msg, emptyFields), msg, emptyFields)

  override def info(msg: => String, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Info, msg, fields), msg, fields)
  }

  override def info(msg: => String, fields: LogField*): IO[Unit] =
    guarded(log(Info, msg, fields), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def infoUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Info, msg, fields, storage)
    } yield ()
    guarded(action, msg, fields, values)
  }

  override def warn(msg: => String): IO[Unit] =
    guarded(log(Warn, msg, emptyFields), msg, emptyFields)

  override def warn(msg: => String, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Warn, msg, fields), msg, fields)
  }

  override def warn(msg: => String, fields: LogField*): IO[Unit] =
    guarded(log(Warn, msg, fields), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def warnUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Warn, msg, fields, storage)
    } yield ()
    guarded(action, msg, fields, values)
  }

  override def error(msg: => String): IO[Unit] =
    guarded(log(Error, msg, emptyFields), msg, emptyFields)

  override def error(msg: => String, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Error, msg, fields), msg, fields)
  }

  override def error(msg: => String, fields: LogField*): IO[Unit] =
    guarded(log(Error, msg, fields), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def errorUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Error, msg, fields, storage)
    } yield ()
    guarded(action, msg, fields, values)
  }

  override def error(msg: => String, e: Throwable): IO[Unit] =
    guarded(log(Error, msg, emptyFields, throwable = Some(e)), msg, emptyFields)

  override def error(msg: => String, e: Throwable, field: LogField): IO[Unit] = {
    val fields = field :: Nil
    guarded(log(Error, msg, fields, throwable = Some(e)), msg, fields)
  }

  override def error(msg: => String, e: Throwable, fields: LogField*): IO[Unit] =
    guarded(log(Error, msg, fields, throwable = Some(e)), msg, fields)

  /** Values are added to the context, not based on this log event only
    */
  override def errorUpdateContext(
      msg: => String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    val action = for {
      storage <- contextOps.updateValues(values)
      _ <- logWithStorage(Error, msg, fields, storage, throwable = Some(e))
    } yield ()
    guarded(action, msg, fields, values)
  }

  private def generateId(): String = {
    val random = java.util.concurrent.ThreadLocalRandom.current()
    new UUID(random.nextLong(), random.nextLong()).toString
  }

  private def prepareStorage(
                              storage: IOStorage,
                              sampleRequest: Option[Boolean],
                              correlationId: Option[String],
                              requestId: Option[String],
                              fields: Seq[LogField],
                              config: Option[BridgeLoggerConfig],
                            ): IOStorage = {
    val rid = requestId match {
      case Some(value)                        => value
      case None if storage.requestId.nonEmpty => storage.requestId
      case None                               => generateId()
    }
    val cid = correlationId match {
      case Some(value)                          => value
      case None if storage.correlationId.nonEmpty => storage.correlationId
      case None                                 => generateId()
    }
    val sampled = if (sampleRequest.isDefined) sampleRequest else storage.sampled
    val now = System.currentTimeMillis()
    val updatedValues =
      if (fields.isEmpty) storage.values
      else if (storage.values.isEmpty) fieldsToMap(fields)
      else storage.values ++ fieldsToMap(fields)

    storage.copy(
      requestId = rid,
      correlationId = cid,
      values = updatedValues,
      sampled = sampled,
      config = config,
      startTime = Some(now),
    )
  }

  override def withRequest[A](
      sampleRequest: Option[Boolean] = None,
      correlationId: Option[String] = None,
      requestId: Option[String] = None,
      composable: Boolean = true,
  )(fa: IO[A])(fields: LogField*)(implicit config: Option[BridgeLoggerConfig] = None): IO[A] = {
    if (composable) {
      contextOps.get.flatMap { storage =>
        val updated = prepareStorage(storage, sampleRequest, correlationId, requestId, fields, config)
        withRequestInternal(updated, storage)(fa)
      }
    } else {
      val updated = prepareStorage(IOStorage.empty, sampleRequest, correlationId, requestId, fields, config)
      withRequestInternal(updated, IOStorage.empty)(fa)
    }
  }

  private def withRequestInternal[A](newStorage: IOStorage, oldStorage: IOStorage)(
      fa: IO[A],
  ): IO[A] = {
    def complete(effect: LogField => IO[Unit]): IO[Unit] =
      effect("endTime" -> System.currentTimeMillis())
        .handleErrorWith(_ => IO.unit)

    val faGuarantee = fa.guaranteeCase {
      case Outcome.Succeeded(_) => complete(endTime => info("Request Completed", endTime))
      case Outcome.Errored(e) =>
        complete(endTime => error("Request failed with exception", e, endTime))
      case Outcome.Canceled() => complete(endTime => warn("Request Cancelled", endTime))
    }

    (ioStorage.set(newStorage) >> faGuarantee).guarantee(ioStorage.set(oldStorage))
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
    contextOps.get.flatMap { storage =>
      val base = storage.config.getOrElse(bridgeLoggerConfig)
      val config = base.copy(
        minLevel = minLevel.getOrElse(base.minLevel),
        replayAllLogLevel = replayAllLogLevel.getOrElse(base.replayAllLogLevel),
        duplicateEntriesOnBufferDump =
          duplicateEntriesOnBufferDump.getOrElse(base.duplicateEntriesOnBufferDump),
        sampleRate = sampleRate.getOrElse(base.sampleRate),
        sampleBelowMinLevel = sampleBelowMinLevel.getOrElse(base.sampleBelowMinLevel),
        bufferBelowMinLevel = bufferBelowMinLevel.getOrElse(base.bufferBelowMinLevel),
        bufferSize = bufferSize.getOrElse(base.bufferSize),
      )
      contextOps.updateConfig(config)
    }
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
      fallbackResponse: FallbackResponse = FallbackResponse.noop,
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

    def withFallback(fallbackResponse: FallbackResponse): builder = {
      copy(fallbackResponse = fallbackResponse)
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
          // Previously omitted, so `withFallback(...)` had no effect: every built logger silently
          // used the default no-op fallback no matter what was configured on the builder.
          fallbackResponse = this.fallbackResponse,
        )
      }
    }
  }
}
