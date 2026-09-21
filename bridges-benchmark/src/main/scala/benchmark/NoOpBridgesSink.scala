package benchmark

import cats.effect.IO
import logEvent.LogEvent
import logSink.LogSink

object NoOpBridgesSink extends LogSink {
  override def log(event: LogEvent): IO[Unit] = IO.unit
}
