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

package runtime

import cats.effect.IO
import java.util.UUID
import logEvent.LogField
import logEvent.LogValue
import logger.BridgeLogger

/** Bridge is loosely a singleton logging provider to allow wiring logging into objects without
  * explicitly injecting it.
  *
  * Those who wish for direct dependency injection can avoid this break in functional paradigms.
  */
object Bridge {
  @volatile
  private var logger: BridgeLogger = _

  def initialize(instance: BridgeLogger): IO[Unit] = {
    IO {
      if (logger != null) {
        throw new IllegalStateException("Bridge as already been initialized")
      }
      logger = instance
    }
  }

  private def isNull: Boolean = {
    logger == null
  }

  private def bridge: BridgeLogger = {
    if (isNull) {
      throw new IllegalStateException("Bridge has not yet been initialized")
    } else logger
  }

  def replace(instance: BridgeLogger): IO[Unit] = {
    IO {
      if (isNull) {
        throw new IllegalStateException("Bridge has not yet been initialized")
      } else {
        logger = instance
      }
    }
  }

  def shutdown: IO[Unit] = {
    IO {
      if (isNull) {
        throw new IllegalStateException("Bridge has not been initialized")
      } else {
        logger = null
      }
    }
  }

  def withBridge[A](f: BridgeLogger => IO[A]): IO[A] = {
    IO(bridge).flatMap(f)
  }

  def trace(msg: => String, fields: LogField*): IO[Unit] = withBridge(_.trace(msg, fields: _*))

  def traceUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = bridge.traceUpdateContext(msg, values, fields: _*)

  def debug(msg: => String, fields: LogField*): IO[Unit] = withBridge(_.debug(msg, fields: _*))

  def debugUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = withBridge(_.debugUpdateContext(msg, values, fields: _*))

  def info(msg: => String, fields: LogField*): IO[Unit] = withBridge(_.info(msg, fields: _*))

  def infoUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = withBridge(_.infoUpdateContext(msg, values, fields: _*))

  def warn(msg: => String, fields: LogField*): IO[Unit] = withBridge(_.warn(msg, fields: _*))

  def warnUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = withBridge(_.warnUpdateContext(msg, values, fields: _*))

  def error(msg: => String, fields: LogField*): IO[Unit] = withBridge(_.error(msg, fields: _*))

  def errorUpdateContext(
      msg: => String,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = withBridge(_.errorUpdateContext(msg, values, fields: _*))

  def error(msg: => String, e: Throwable, fields: LogField*): IO[Unit] =
    withBridge(_.error(msg, e, fields: _*))

  def errorUpdateContext(
      msg: => String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): IO[Unit] = {
    withBridge(_.errorUpdateContext(msg, e, values, fields: _*))
  }

  def withRequest[A](
      sampleRequest: Option[Boolean] = None,
      correlationId: Option[String] = None,
      requestId: Option[String] = None,
  )(fa: IO[A])(fields: LogField*): IO[A] =
    withBridge(_.withRequest[A](sampleRequest, correlationId, requestId)(fa)(fields: _*))

  def updateValues(key: String, value: LogValue): IO[Unit] = withBridge(_.updateValues(key, value))

  def setCorrelationId(id: String): IO[Unit] = withBridge(_.setCorrelationId(id))

  def setRequestId(id: String): IO[Unit] = withBridge(_.setRequestId(id))

}
