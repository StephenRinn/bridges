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
