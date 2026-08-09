import cats.effect.IO
import logger.traceContext.{TraceContext, TraceContextProvider}
import org.typelevel.otel4s.trace.Tracer

final class Otel4sBridge(
    tracer: Tracer[IO],
) extends TraceContextProvider {
  override def current: IO[Option[TraceContext]] =
    tracer.currentSpanContext.map(_.map { spanContext =>
      TraceContext(
        traceId = Some(spanContext.traceId.toString()),
        spanId = Some(spanContext.spanId.toString()),
      )
    })
}
