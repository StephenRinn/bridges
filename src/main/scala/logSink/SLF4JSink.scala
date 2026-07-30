package logSink

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import logEvent.LogEvent

class SLF4JSink extends LogSink with LazyLogging {

  private def format(logEvent: LogEvent): String = {
    val ctx = logEvent.context
    val values =
      if (ctx.values.isEmpty) "-"
      else ctx.values.map { case (k, v) => s"$k=$v" }.mkString(", ")

    val throwO = if (logEvent.throwable.isEmpty) "" else {
      s"[Error=${logEvent.throwable.get}]"
    }

    val duration = (ctx.endTime, ctx.startTime) match {
      case (Some(end), Some(start)) => Some(end - start)
      case _ => None
    }
    s"""[timestamp=${System.currentTimeMillis()}] [level=${logEvent.level}] [cid=${ctx.correlationId}]
       | [rid=${ctx.requestId}] [duration=$duration] [values=$values] [message=${logEvent.message}] $throwO""".stripMargin
  }


  override def trace(event: IO[LogEvent]): IO[Unit] = {
    for {
      log <- event
      formattedLog = format(log)
    } yield
      IO.blocking(logger.trace(formattedLog))
  }

  override def debug(event: IO[LogEvent]): IO[Unit] = {
    for {
      log <- event
      formattedLog = format(log)
    } yield
      IO.blocking(logger.debug(formattedLog))
  }

  override def info(event: IO[LogEvent]): IO[Unit] = {
    for {
      log <- event
      formattedLog = format(log)
    } yield
      IO.blocking(logger.info(formattedLog))


  }

  override def warn(event: IO[LogEvent]): IO[Unit] = {
    for{
      log <- event
      formattedLog = format(log)
    } yield
    IO.blocking(logger.warn(formattedLog))
  }

  override def error(event: IO[LogEvent]): IO[Unit] = {
    for{
      log <- event
      formattedLog = format(log)
    } yield
    IO.blocking(logger.error(formattedLog))
  }
}
