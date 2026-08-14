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
import java.util.UUID
import logEvent.LogValue
import logger.BridgeLogger
import org.http4s.Header
import org.http4s.HttpApp
import org.typelevel.ci.CIStringSyntax

object BridgeMiddleware {
  def apply(
      logger: BridgeLogger,
      defaultValues: Map[String, LogValue] = Map[String, LogValue]().empty,
  ): HttpApp[IO] => HttpApp[IO] =
    app =>
      HttpApp { request =>
        val correlationId =
          request.headers
            .get(ci"X-Correlation-ID")
            .map(_.head.value)
            .getOrElse(UUID.randomUUID().toString)

        val correlationHeader = Header.Raw(ci"X-Correlation-ID", correlationId)

        val requestId = UUID.randomUUID().toString
        logger
          .withRequest(
            values = defaultValues,
            correlationId = correlationId,
            requestId = requestId,
          )(app(request))
          .map(_.putHeaders(correlationHeader))
      }
}
