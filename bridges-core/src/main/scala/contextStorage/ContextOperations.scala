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
import logger.config.BridgeLoggerConfig

final class ContextOperations(
                               private val local: IOLocal[IOStorage],
                               private val maxBuffer: Int = 200,
                             ) {
  def modify(f: IOStorage => IOStorage): IO[Unit] = local.update(f)

  def clear: IO[Unit] = local.set(IOStorage.empty)

  def setCorrelation(id: String): IO[Unit] = { local.update(_.copy(correlationId = id)) }

  def setRequest(requestId: String): IO[Unit] = { local.update(_.copy(requestId = requestId)) }

  def updateFields(fields: LogField*): IO[IOStorage] = {
    updateValues(fields.iterator.map(field => field.key -> field.value()).toMap)
  }

  def updateValue(key: String, value: LogValue): IO[Unit] = {
    local.modify { storage =>
      val updated = storage.values + (key -> value)
      (storage.copy(values = updated), ())
    }
  }

  // Returns the storage *after* the update so callers that immediately need the current
  // context (e.g. the `*UpdateContext` logging methods) can reuse it instead of paying for a
  // second, separate `IOLocal.get` round trip.
  def updateValues(updatedValues: Map[String, LogValue]): IO[IOStorage] = {
    local.modify { storage =>
      val updated = storage.copy(values = storage.values ++ updatedValues)
      (updated, updated)
    }
  }

  def updateValue[A: ToLogValue](key: String, value: A): IO[Unit] = {
    updateValue(key, ToLogValue[A].toLogValue(value))
  }

  // `rebuildLogSize` is maintained incrementally so the common (below-capacity) path never has to
  // walk the whole list just to answer "how big is this?" - a plain `List.size` check here would
  // make every buffered log call in a request O(n), making a request that buffers n logs O(n^2)
  // overall. Only once the buffer is actually at capacity do we pay the (bounded, O(maxBuffer))
  // cost of trimming it.
  def updateRebuildLog(event: LogEvent): IO[Unit] = {
    val modifiedEvent = RebuildLog(event.toStoredLog)
    local.update { storage =>
      if (storage.rebuildLogSize >= maxBuffer) {
        // `storage.rebuildLog` is already exactly `maxBuffer` long (that's the invariant this
        // method maintains), so the trimmed list is always exactly `maxBuffer` long too - no need
        // to call `.size` again to find that out.
        val updated = (modifiedEvent :: storage.rebuildLog).take(maxBuffer)
        storage.copy(rebuildLog = updated, rebuildLogSize = maxBuffer)
      } else {
        storage.copy(
          rebuildLog = modifiedEvent :: storage.rebuildLog,
          rebuildLogSize = storage.rebuildLogSize + 1,
        )
      }
    }
  }

  def clearRebuildLogs: IO[Unit] = {
    local.update(_.copy(rebuildLog = List.empty[RebuildLog], rebuildLogSize = 0))
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
