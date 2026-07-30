package logSink

import cats.effect.{IO, IOLocal}
import contextStorage.IOStorage
import logEvent._


class IOBridgeSink(ioStorage: IOLocal[IOStorage]) extends LogSink {

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

  private def logIO(event: IO[LogEvent]): IO[Unit] = {
    for {
      log <- event
      formattedMsg = format(log)
    } yield
      IO.blocking {
        println(formattedMsg)
      }
  }

  override def trace(event: IO[LogEvent]): IO[Unit] = {
    logIO(event)
  }

  override def debug(event: IO[LogEvent]): IO[Unit] = {
    logIO(event)
  }

  override def info(event: IO[LogEvent]): IO[Unit] = {
    logIO(event)
  }

  override def warn(event: IO[LogEvent]): IO[Unit] = {
    logIO(event)
  }

  override def error(event: IO[LogEvent]): IO[Unit] = {
    logIO(event)
  }
}
