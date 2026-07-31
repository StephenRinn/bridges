package contextStorage

import cats.effect.{IO, IOLocal}

final class ContextOperations(private val local: IOLocal[IOStorage]) {
  def modify(f: IOStorage => IOStorage): IO[Unit] = local.update(f)

  def clear: IO[Unit] = local.set(IOStorage.empty)

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

  def markEnd(endTime: Long): IO[Unit] = {
    local.update(_.copy(endTime = Some(endTime)))
  }

  def get: IO[IOStorage] = {
    local.get
  }
}
