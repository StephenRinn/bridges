package logger.traceContext

import cats.effect.IO
import logEvent.LogValue

trait TraceContextProvider {
  def attributes: IO[Map[String, LogValue]]
}

object TraceContextProvider {
  val noop: TraceContextProvider =
    new TraceContextProvider {
      override def attributes: IO[Map[String, LogValue]] = IO.pure(Map[String, LogValue]().empty)
    }
}
