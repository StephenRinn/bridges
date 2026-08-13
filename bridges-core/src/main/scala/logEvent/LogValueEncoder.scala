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

package logEvent

import io.circe.Json
import logEvent.LogValue._

object LogValueEncoder {
  def encode(value: LogValue): Json =
    value match {
      case Null =>
        Json.Null

      case StringValue(value) =>
        Json.fromString(value)

      case BooleanValue(value) =>
        Json.fromBoolean(value)

      case IntValue(value) =>
        Json.fromInt(value)

      case LongValue(value) =>
        Json.fromLong(value)

      case DoubleValue(value) =>
        Json.fromDoubleOrNull(value)

      case ListValue(values) =>
        Json.fromValues(values.map(encode))

      case MapValue(values) =>
        Json.obj(
          values.iterator.map { case (key, value) =>
            key -> encode(value)
          }.toSeq: _*,
        )
    }

  def encodeAttribute(
      values: Map[String, LogValue],
  ): Json =
    Json.obj(
      values.iterator.map { case (key, value) =>
        key -> encode(value)
      }.toSeq: _*,
    )
}
