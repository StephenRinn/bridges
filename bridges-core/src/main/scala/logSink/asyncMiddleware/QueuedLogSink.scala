package logSink.asyncMiddleware

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Queue
import logEvent.LogEvent
import logSink.LogSink
import logSink.asyncMiddleware.config.LogDeliveryFailure._
import logSink.asyncMiddleware.config.QueueCapacity._
import logSink.asyncMiddleware.config.QueueOverflow._
import logSink.asyncMiddleware.config.QueuedLogSinkConfig
import logSink.asyncMiddleware.policies.LogDeliveryPolicy

class QueuedLogSink private (
    queue: Queue[IO, LogEvent],
    config: QueuedLogSinkConfig,
) extends LogSink {
  override def log(event: LogEvent): IO[Unit] = {
    config.overflow match {
      case Block => queue.offer(event)
      case _ =>
        queue.tryOffer(event).void
    }
  }
}

object QueuedLogSink {

  private def createQueue(
      config: QueuedLogSinkConfig,
  ): IO[Queue[IO, LogEvent]] = {
    config.capacity match {
      case Unbounded => Queue.unbounded[IO, LogEvent]
      case Bounded(max) => Queue.bounded[IO, LogEvent](max)
    }
  }

  def createResource(
      logSink: LogSink,
      config: QueuedLogSinkConfig,
      policies: LogDeliveryPolicy*,
  ): Resource[IO, LogSink] = {
    for {
      queue <- Resource.eval(createQueue(config))

      _ <- Resource.make {
        queue.take
          .flatMap { event =>
            val logAction = logSink.log(event)
            val fa = policies.foldLeft(logAction) { case (current, policy) =>
              policy.apply(current)
            }

            config.logDeliveryFailure match {
              case Drop =>
                fa.handleErrorWith { error =>
                  // TODO decide final handling for drop if any
                  IO.unit
                }
              case Stop => fa
            }
          }
          .foreverM
          .start
      }(_.cancel)
    } yield new QueuedLogSink(queue, config)
  }
}
