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

package runtime

import cats.effect.IO
import java.util.UUID
import logger.BridgeLogger

/**
 * Bridge is loosely a singleton logging provider to allow
 * wiring logging into objects without explicitly injecting it.
 *
 * Those who wish for direct dependency injection can avoid this
 * break in functional paradigms.
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

  private def bridge: BridgeLogger = {
    if (logger == null) {
      throw new IllegalStateException("Bridge has not yet been initialized")
    } else logger
  }

  def trace(msg: String): IO[Unit] = logger.trace(msg)

  def trace(msg: String, values: Map[String, String]): IO[Unit] = logger.trace(msg, values)

  def debug(msg: String): IO[Unit] = logger.debug(msg)

  def debug(msg: String, values: Map[String, String]): IO[Unit] = logger.debug(msg, values)

  def info(msg: String): IO[Unit] = logger.info(msg)

  def info(msg: String, values: Map[String, String]): IO[Unit] = logger.info(msg, values)

  def warn(msg: String): IO[Unit] = logger.warn(msg)

  def warn(msg: String, values: Map[String, String]): IO[Unit] = logger.warn(msg, values)

  def error(msg: String): IO[Unit] = logger.error(msg)

  def error(msg: String, values: Map[String, String]): IO[Unit] = logger.error(msg, values)

  def error(msg: String, e: Throwable): IO[Unit] = logger.error(msg, e)

  def error(msg: String, e: Throwable, values: Map[String, String]): IO[Unit] =
    logger.error(msg, e, values)

  def withRequest[A](
      values: Map[String, String] = Map[String, String](),
      sampleRequest: Option[Boolean] = None,
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
  )(fa: IO[A]): IO[A] = logger.withRequest[A](values, sampleRequest, correlationId, requestId)(fa)

  def updateValues(key: String, value: String): IO[Unit] = logger.updateValues(key, value)

  def setCorrelationId(id: String): IO[Unit] = logger.setCorrelationId(id)

  def setRequestId(id: String): IO[Unit] = logger.setRequestId(id)

}
