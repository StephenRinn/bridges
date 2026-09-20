package benchmark

import cats.effect.IO
import logEvent.LogEvent
import logSink.LogSink

object NoOpBridgeSink extends LogSink {
  override def log(event: LogEvent): IO[Unit] = IO.unit
}