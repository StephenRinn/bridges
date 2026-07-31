package logEvent

sealed trait LogLevel
object LogLevel extends {
  case object Trace extends LogLevel
  case object Debug extends LogLevel
  case object Info extends LogLevel
  case object Warn extends LogLevel
  case object Error extends LogLevel
}
