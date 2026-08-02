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
    new GenericBridgeLogger[F]{

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
