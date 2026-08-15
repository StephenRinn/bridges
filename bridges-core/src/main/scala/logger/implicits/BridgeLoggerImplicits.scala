package logger.implicits

import cats.effect.IO
import logEvent.LogField
import logEvent.LogLevel._
import logger.{BridgeLogger, BridgeLoggerConfig}

object BridgeLoggerImplicits {
  implicit class ops(logger: BridgeLogger) {

    def traceImpl(msg: String, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
          _ <- logger.log(
            level = Trace,
            message = msg,
            fields = fields,
            throwable = None,
            config = Some(config),
          )
        } yield ()
    }

    def debugImpl(msg: String, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
        _ <- logger.log(
          level = Debug,
          message = msg,
          fields = fields,
          throwable = None,
          config = Some(config),
        )
      } yield ()
    }

    def infoImpl(msg: String, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
        _ <- logger.log(
          level = Info,
          message = msg,
          fields = fields,
          throwable = None,
          config = Some(config),
        )
      } yield ()
    }

    def warnImpl(msg: String, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
        _ <- logger.log(
          level = Warn,
          message = msg,
          fields = fields,
          throwable = None,
          config = Some(config),
        )
      } yield ()
    }

    def errorImpl(msg: String, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
        _ <- logger.log(
          level = Error,
          message = msg,
          fields = fields,
          throwable = None,
          config = Some(config),
        )
      } yield ()
    }

    def errorImpl(msg: String, e: Throwable, fields: LogField*)(implicit config: BridgeLoggerConfig): IO[Unit] = {
      for {
        _ <- logger.log(
          level = Trace,
          message = msg,
          fields = fields,
          throwable = Some(e),
          config = Some(config),
        )
      } yield ()
    }
  }
}
