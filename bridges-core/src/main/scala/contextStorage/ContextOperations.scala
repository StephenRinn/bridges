package contextStorage

import cats.effect.{IO, IOLocal}
import logEvent.{LogEvent, LogLevel}

final class ContextOperations(
    private val local: IOLocal[IOStorage],
    private val maxBuffer: Int = 200,
) {
  def modify(f: IOStorage => IOStorage): IO[Unit] = local.update(f)

  def clear: IO[Unit] = local.set(IOStorage.empty)

  def setCorrelation(id: String): IO[Unit] = { local.update(_.copy(correlationId = id)) }

  def setRequest(requestId: String): IO[Unit] = { local.update(_.copy(requestId = requestId)) }

  def updateValues(key: String, value: String): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.values + (key -> value)
      (storage.copy(values = updated), ())
    }
  }

  def updateValues(updatedValues: Map[String, String]): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.values ++ updatedValues
      (storage.copy(values = updated), ())
    }
  }

  def updateRebuildLog(event: LogEvent, level: LogLevel): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.rebuildLog :+ RebuildLog(event, level)
      if (updated.size <= maxBuffer) {
        (storage.copy(rebuildLog = updated), ())
      } else {
        (storage.copy(rebuildLog = updated.tail), ())
      }
    }
  }

  def clearRebuildLogs: IO[Unit] = {
    local.update(_.copy(rebuildLog = List[RebuildLog]().empty))
  }

  def setSampled(sampled: Boolean): IO[Unit] = {
    local.update(_.copy(sampled = Some(sampled)))
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
