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

    val throwO = if (logEvent.throwable.isEmpty) "" else {
      s"[Error=${logEvent.throwable.get}]"
    }

    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => Some(end - start)
      case _ => None
    }
    s"""[timestamp=${System.currentTimeMillis()}] [level=${logEvent.level}] [cid=${ctx.correlationId}]
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
