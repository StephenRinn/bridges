package logger.traceContext

import cats.effect.IO

trait TraceContextProvider {
  def current: IO[Option[TraceContext]]
}
