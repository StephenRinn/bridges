package logger.traceContext

import cats.effect.IO

trait TraceContextProvider {
  def current: IO[Option[TraceContext]]
}

object TraceContextProvider {
  val noop: TraceContextProvider =
    new TraceContextProvider {
      override def current: IO[Option[TraceContext]] = IO.pure(None)
    }
}
