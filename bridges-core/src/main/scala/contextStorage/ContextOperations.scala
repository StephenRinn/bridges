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

package contextStorage

import cats.effect.IO
import cats.effect.IOLocal
import logEvent.LogEvent
import logEvent.LogField
import logEvent.LogLevel
import logEvent.LogValue
import logEvent.ToLogValue
import logger.BridgeLoggerConfig

final class ContextOperations(
    private val local: IOLocal[IOStorage],
    private val maxBuffer: Int = 200,
) {
  def modify(f: IOStorage => IOStorage): IO[Unit] = local.update(f)

  def clear: IO[Unit] = local.set(IOStorage.empty)

  def setCorrelation(id: String): IO[Unit] = { local.update(_.copy(correlationId = id)) }

  def setRequest(requestId: String): IO[Unit] = { local.update(_.copy(requestId = requestId)) }

  def updateFields(fields: LogField*): IO[Unit] = {
    updateValues(fields.iterator.map(field => field.key -> field.value()).toMap)
  }

  def updateValue(key: String, value: LogValue): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.values + (key -> value)
      (storage.copy(values = updated), ())
    }
  }

  def updateValues(updatedValues: Map[String, LogValue]): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.values ++ updatedValues
      (storage.copy(values = updated), ())
    }
  }

  def updateValue[A: ToLogValue](key: String, value: A): IO[Unit] = {
    updateValue(key, ToLogValue[A].toLogValue(value))
  }

  def updateRebuildLog(event: LogEvent, level: LogLevel): IO[Unit] = {
    val modifiedEvent = event.toStoredLog
    local.modify { storage =>
      val updated = storage.rebuildLog :+ RebuildLog(modifiedEvent)
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

  def updateConfig(config: BridgeLoggerConfig): IO[Unit] = {
    local.update(_.copy(config = Some(config)))
  }

  def tempConfigUpdate(config: BridgeLoggerConfig): IO[Option[BridgeLoggerConfig]] = {
    for {
      storage <- local.get
      _ <- local.update(_.copy(config = Some(config)))
    } yield storage.config
  }
}
