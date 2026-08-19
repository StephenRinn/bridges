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

import cats.effect.IO
import cats.effect.LiftIO
import cats.~>
import java.util.UUID
import logEvent.LogField
import logEvent.LogValue

trait GenericBridgeLogger[F[_]] {
  def trace(msg: => String, fields: LogField*): F[Unit]
  def traceUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): F[Unit]
  def debug(msg: => String, fields: LogField*): F[Unit]
  def debugUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): F[Unit]
  def info(msg: => String, fields: LogField*): F[Unit]
  def infoUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): F[Unit]
  def warn(msg: => String, fields: LogField*): F[Unit]
  def warnUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): F[Unit]
  def error(msg: => String, fields: LogField*): F[Unit]
  def errorUpdateContext(msg: => String, values: Map[String, LogValue], fields: LogField*): F[Unit]
  def error(msg: => String, e: Throwable, fields: LogField*): F[Unit]
  def errorUpdateContext(
      msg: => String,
      e: Throwable,
      values: Map[String, LogValue],
      fields: LogField*,
  ): F[Unit]
  def withRequest[A](
      sampleRequest: Option[Boolean] = None,
      correlationId: String = UUID.randomUUID().toString,
      requestId: String = UUID.randomUUID().toString,
      transform: F ~> IO,
  )(fa: F[A])(fields: LogField*): F[A]
  def updateValues(key: String, value: LogValue): F[Unit]
  def setCorrelationId(id: String): F[Unit]
  def setRequestId(id: String): F[Unit]
}

object GenericBridgeLogger {

  def fromBridge[F[_]: LiftIO](bridge: BridgeLogger): GenericBridgeLogger[F] = {
    new GenericBridgeLogger[F] {

      override def trace(msg: => String, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.trace(msg, fields: _*))
      }

      override def traceUpdateContext(
          msg: => String,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.traceUpdateContext(msg, values, fields: _*))
      }

      override def debug(msg: => String, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.debug(msg, fields: _*))
      }

      override def debugUpdateContext(
          msg: => String,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.debugUpdateContext(msg, values, fields: _*))
      }

      override def info(msg: => String, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.info(msg, fields: _*))
      }

      override def infoUpdateContext(
          msg: => String,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.infoUpdateContext(msg, values, fields: _*))
      }

      override def warn(msg: => String, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.warn(msg, fields: _*))
      }

      override def warnUpdateContext(
          msg: => String,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.warnUpdateContext(msg, values, fields: _*))
      }

      override def error(msg: => String, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.error(msg, fields: _*))
      }

      override def errorUpdateContext(
          msg: => String,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.errorUpdateContext(msg, values, fields: _*))
      }

      override def error(msg: => String, e: Throwable, fields: LogField*): F[Unit] = {
        LiftIO[F].liftIO(bridge.error(msg, e, fields: _*))
      }

      override def errorUpdateContext(
          msg: => String,
          e: Throwable,
          values: Map[String, LogValue],
          fields: LogField*,
      ): F[Unit] = {
        LiftIO[F].liftIO(bridge.errorUpdateContext(msg, e, values, fields: _*))

      }

      override def withRequest[A](
          sampleRequest: Option[Boolean] = None,
          correlationId: String = UUID.randomUUID().toString,
          requestId: String = UUID.randomUUID().toString,
          transform: F ~> IO,
      )(fa: F[A])(fields: LogField*): F[A] = {

        LiftIO[F].liftIO(
          bridge.withRequest[A](
            sampleRequest = sampleRequest,
            correlationId = Some(correlationId),
            requestId = Some(requestId),
          )(transform(fa))(fields: _*),
        )
      }

      override def updateValues(key: String, value: LogValue): F[Unit] = {
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
