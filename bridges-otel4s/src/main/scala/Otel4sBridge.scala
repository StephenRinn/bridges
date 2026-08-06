import cats.effect.IO
import cats.mtl.Local
import logger.traceContext.TraceContext
import logger.traceContext.TraceContextProvider
import org.typelevel.otel4s.sdk.context.Context
import org.typelevel.otel4s.trace.Tracer

final class Otel4sBridge(implicit
    local: Local[IO, Context],
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
