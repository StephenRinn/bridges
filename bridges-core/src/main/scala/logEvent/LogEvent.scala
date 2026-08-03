package logEvent

import contextStorage.IOStorage

case class LogEvent(
    level: LogLevel,
    message: String,
    timestamp: Long,
    context: IOStorage,
    throwable: Option[Throwable] = None,
)
