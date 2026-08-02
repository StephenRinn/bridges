import cats.effect.IO
import java.util.UUID
import logger.BridgeLogger
import org.http4s.Header
import org.http4s.HttpApp
import org.typelevel.ci.CIStringSyntax

object BridgeMiddleware {
  def apply(
      logger: BridgeLogger,
      defaultValues: Map[String, String] = Map[String, String]().empty,
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
