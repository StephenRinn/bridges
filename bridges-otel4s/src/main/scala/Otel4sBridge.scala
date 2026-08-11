import cats.effect.IO
import logEvent.LogEvent.attribute
import logEvent._
import logger.traceContext.TraceContextProvider
import org.typelevel.otel4s.trace.Tracer

final class Otel4sBridge(
    tracer: Tracer[IO],
) extends TraceContextProvider {
  override def attributes: IO[Map[String, LogValue]] = {
    tracer.currentSpanContext.map(_.map { spanContext =>
      Map(
        attribute("traceid", spanContext.traceId.toString()),
        attribute("spanid", spanContext.spanId.toString()),
        attribute("traceflags", spanContext.traceFlags.toString()),
      )
    }.getOrElse(Map[String, LogValue]().empty))
  }
}
