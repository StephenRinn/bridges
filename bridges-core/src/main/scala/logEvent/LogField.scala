package logEvent

import scala.language.implicitConversions

final case class LogField (
    key: String,
    value: () => LogValue,
                    )

object LogField {
  implicit def fromTuple[A: ToLogValue](value: (String, A)): LogField = {
    LogField(
      key = value._1,
      value = () => ToLogValue[A].toLogValue(value._2)
    )
  }
}