package contextStorage

import cats.effect.kernel.Outcome
import cats.effect.{Clock, IO, IOLocal}
import java.util.UUID
import logger.BridgeLogger

class ContextOperations(local: IOLocal[IOStorage], logger: BridgeLogger) {
  def setCorrelation(id: String): IO[Unit] = { local.update(_.copy(correlationId = id)) }

  def setRequest(requestId: String): IO[Unit] = { local.update(_.copy(requestId = requestId))}

  def updateValues(key: String, value: String): IO[Unit] = {
    local.modify{ storage =>
      val updated = storage.values + (key -> value)
      (storage.copy(values = updated), ())
    }
  }

  def markStart(startTime: Long): IO[Unit] = {
    local.update(_.copy(startTime = Some(startTime)))
  }
  def markStart: IO[Unit] = {
    local.update(_.copy(startTime = Some(System.currentTimeMillis())))
  }

  def markEnd(endTime: Long): IO[Unit] = {
    local.update(_.copy(endTime = Some(endTime)))
  }
  def markEnd: IO[Unit] = {
    local.update(_.copy(endTime = Some(System.currentTimeMillis())))
  }

  def get: IO[IOStorage] = {
    local.get
  }

  def withRequest[A](correlationId: String = UUID.randomUUID().toString)(implicit fa: IO[A]): IO[A] =
    for {
      _ <- setRequest(UUID.randomUUID().toString)
      _ <- setCorrelation(correlationId)
      start <- Clock[IO].monotonic
      _ <- markStart(start.toMillis)
      result <- fa.guaranteeCase {
        case Outcome.Succeeded(fa) =>
          for {
          end <- Clock[IO].monotonic
          _ <- markEnd(end.toMillis)
          _ <- logger.info("Request Completed")
        } yield ()
        case Outcome.Errored(e) =>
          for {
            end <- Clock[IO].monotonic
            _ <- markEnd(end.toMillis)
            _ <- logger.error("RequestFailed", e)
          } yield ()
        case Outcome.Canceled() =>
          for {
            end <- Clock[IO].monotonic
            _ <- markEnd(end.toMillis)
            _ <- logger.warn("Request cancelled")
        } yield ()
      }
    } yield result
}
