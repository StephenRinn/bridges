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
package logger.implicits

import cats.effect.IO
import logEvent.LogField
import logEvent.LogLevel._
import logger.BridgeLogger
import logger.BridgeLoggerConfig

object BridgeLoggerImplicits {
  implicit class ops(logger: BridgeLogger) {

    def traceImpl(msg: => String, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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

    def debugImpl(msg: => String, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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

    def infoImpl(msg: => String, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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

    def warnImpl(msg: => String, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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

    def errorImpl(msg: => String, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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

    def errorImpl(msg: => String, e: Throwable, fields: LogField*)(implicit
        config: BridgeLoggerConfig,
    ): IO[Unit] = {
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
