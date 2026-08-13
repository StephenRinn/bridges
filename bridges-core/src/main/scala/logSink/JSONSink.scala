/*
 * /*
 *  * Copyright 2026 Stephen Rinn
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  */
 */

package logSink

import cats.effect.IO
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.EncoderOps
import logEvent.LogEvent
import logEvent.LogValueEncoder
import logSink.JsonHelpers._

class JSONSink extends LogSink {
  private def toLoggingJson(logEvent: LogEvent): String = {
    val ctx = logEvent.context
    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => Some(end - start)
      case _ => None
    }

    val values = ctx.values ++ logEvent.logContext

    val attributeObject = LogValueEncoder.encodeAttribute(logEvent.attributes)

    Json
      .obj(
        "timestamp" -> logEvent.timestamp.asJson,
        "cid" -> ctx.correlationId.asJson,
        "rid" -> ctx.requestId.asJson,
        "duration" -> duration.asJson,
        "values" -> values.asJson,
        "attributes" -> attributeObject,
        "level" -> logEvent.level.toString.asJson,
        "message" -> logEvent.message.asJson,
        "error" -> logEvent.throwable.orNull.asJson,
      )
      .noSpaces
  }

  override def log(event: LogEvent): IO[Unit] = {
    IO.blocking(println(toLoggingJson(event)))
  }
}

object JsonHelpers {
  implicit val throwableEncoder: Encoder[Throwable] = {
    Encoder.instance { t =>
      Json.obj(
        "message" -> t.getMessage.asJson,
        "type" -> t.getClass.getName.asJson,
        "stacktrace" -> t.getStackTrace.map(s => s.toString.asJson).asJson,
        "cause" -> t.getCause.toString.asJson,
        "suppressedexceptions" -> t.getSuppressed.map(s => s.toString.asJson).asJson,
      )
    }
  }
}
