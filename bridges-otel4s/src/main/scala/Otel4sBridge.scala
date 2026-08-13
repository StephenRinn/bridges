/*
 * Copyright 2026 Stephen Rinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
