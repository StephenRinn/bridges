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
import com.typesafe.scalalogging.LazyLogging
import logEvent.{LogEvent, LogLevel}

class SLF4JSink extends LogSink with LazyLogging {

  private def format(logEvent: LogEvent): String = {
    val ctx = logEvent.context
    val values =
      if (ctx.values.isEmpty) ""
      else {
        val kVString = ctx.values.map { case (k, v) => s"$k=$v" }.mkString(", ")
        s"[values=$kVString] "
      }

    val throwO =
      if (logEvent.throwable.isEmpty) ""
      else {
        s"[Error=${logEvent.throwable.get}]"
      }

    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => Some(end - start)
      case _ => None
    }
    s"""[timestamp=${logEvent.timestamp}] [level=${logEvent.level}] [cid=${ctx.correlationId}]
       | [rid=${ctx.requestId}] [duration=$duration] $values[message=${logEvent.message}] $throwO""".stripMargin
  }

  override protected def log(event: LogEvent): IO[Unit] = {
    val formattedLog = format(event)
    event.level match {
      case LogLevel.Trace => IO.blocking(logger.trace(formattedLog))
      case LogLevel.Debug => IO.blocking(logger.debug(formattedLog))
      case LogLevel.Info => IO.blocking(logger.info(formattedLog))
      case LogLevel.Warn => IO.blocking(logger.warn(formattedLog))
      case LogLevel.Error => IO.blocking(logger.error(formattedLog))
    }
  }
}
