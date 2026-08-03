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

import cats.effect.LiftIO

trait GenericBridgeLogger[F[_]] {
  def trace(msg: String): F[Unit]
  def debug(msg: String): F[Unit]
  def info(msg: String): F[Unit]
  def warn(msg: String): F[Unit]
  def error(msg: String): F[Unit]
  def error(msg: String, e: Throwable): F[Unit]
  def updateValues(key: String, value: String): F[Unit]
  def setCorrelationId(id: String): F[Unit]
  def setRequestId(id: String): F[Unit]
}

object GenericBridgeLogger {

  def fromBridge[F[_]: LiftIO](bridge: BridgeLogger): GenericBridgeLogger[F] = {
    new GenericBridgeLogger[F] {

      override def trace(msg: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.trace(msg))
      }

      override def debug(msg: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.debug(msg))
      }

      override def info(msg: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.info(msg))
      }

      override def warn(msg: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.warn(msg))
      }

      override def error(msg: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.error(msg))
      }

      override def error(msg: String, e: Throwable): F[Unit] = {
        LiftIO[F].liftIO(bridge.error(msg, e))
      }

      override def updateValues(key: String, value: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.updateValues(key, value))
      }

      override def setCorrelationId(id: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.setCorrelationId(id))
      }

      override def setRequestId(id: String): F[Unit] = {
        LiftIO[F].liftIO(bridge.setRequestId(id))
      }
    }
  }

}
