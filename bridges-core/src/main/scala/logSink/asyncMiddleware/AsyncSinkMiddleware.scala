package logSink.asyncMiddleware

import cats.effect.IO
import logEvent.LogEvent
import logSink.LogSink
import logSink.asyncMiddleware.policies.LogDeliveryPolicy

class AsyncSinkMiddleware(sink: LogSink, asyncPolicies: LogDeliveryPolicy*)
    extends LogSink {

  override def log(event: LogEvent): IO[Unit] = asyncPolicies.foldLeft[IO[Unit]](IO.unit) {
    (acc, policy) =>
      policy.apply(acc)
  }
}
