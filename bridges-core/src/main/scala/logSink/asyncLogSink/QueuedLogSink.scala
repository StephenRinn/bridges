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

package logSink.asyncLogSink

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Queue
import logEvent.LogEvent
import logSink.LogSink
import logSink.asyncLogSink.config.LogDeliveryFailure._
import logSink.asyncLogSink.config.QueueCapacity._
import logSink.asyncLogSink.config.QueueOverflow._
import logSink.asyncLogSink.config.QueuedLogSinkConfig
import logSink.policies.LogDeliveryPolicy

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
