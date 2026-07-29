package logEvent

import contextStorage.IOStorage
import java.time.Instant
import jdk.jfr.internal.LogLevel

case class LogEvent(
    level: LogLevel,
    message: String,
    timestamp: Instant,
    context: IOStorage,
    throwable: Option[Throwable] = None
                   )


