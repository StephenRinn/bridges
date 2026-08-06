package logger.traceContext

final case class TraceContext(
    traceId: Option[String],
    spanId: Option[String],
) {
  def formatTraceString: String = {
    val traceIdO = this.traceId match {
      case Some(value) => s"[TraceId:$value] "
      case None => ""
    }
    val spanIdO = this.spanId match {
      case Some(value) => s"[SpanId:$value] "
      case None => ""
    }
    s"$traceIdO$spanIdO"
  }
}
