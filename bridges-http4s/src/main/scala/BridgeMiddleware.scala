import cats.effect.IO
import contextStorage.IOStorage
import java.util.UUID
import logger.BridgeLogger
import org.http4s.{Header, HttpApp, HttpRoutes}
import org.typelevel.ci.CIStringSyntax

object BridgeMiddleware {
  def apply(logger: BridgeLogger): HttpApp[IO] => HttpApp[IO] =
    app =>
      HttpApp { request =>
        val correlationId =
          request.headers.get(ci"X-Correlation-ID").map(_.head.value)
            .getOrElse(UUID.randomUUID().toString)

        val correlationHeader = Header.Raw(ci"X-Correlation-ID", correlationId)

        val requestId = UUID.randomUUID().toString
        logger.withRequest(correlationId, requestId)( app(request))
          .map(_.putHeaders(correlationHeader))
      }
}
