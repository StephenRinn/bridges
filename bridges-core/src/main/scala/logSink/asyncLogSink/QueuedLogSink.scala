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

import cats.effect.Deferred
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
    workerDeath: Deferred[IO, Throwable],
    shutdown: Deferred[IO, Unit],
) extends LogSink {
  private def enqueue(event: LogEvent): IO[Unit] = {
    config.overflow match {
      case Block =>
        queue.offer(event)

      case DropNewest =>
        queue.tryOffer(event).void
    }
  }

  override def log(event: LogEvent): IO[Unit] = {
    config.logDeliveryFailure match {
      case Stop =>
        IO.race(
          workerDeath.get.flatMap(IO.raiseError),
          IO.race(
            shutdown.get *> IO.raiseError(
              new IllegalStateException(
                "Error: Sink has been shut down.",
              ),
            ),
            enqueue(event),
          ),
        ).flatMap {
          case Left(error) => IO.raiseError(error)
          case Right(result) => IO.pure(result)
        }

      case Drop =>
        IO.race(
          shutdown.get *> IO.raiseError(
            new IllegalStateException(
              "Error: Sink shutdown",
            ),
          ),
          enqueue(event),
        ).flatMap {
          case Left(error) => IO.raiseError(error)
          case Right(result) => IO.pure(result)
        }
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

  private def applyPolicies(
      event: LogEvent,
      logSink: LogSink,
      config: QueuedLogSinkConfig,
      policies: LogDeliveryPolicy*,
  ): IO[Unit] = {
    val logAction = logSink.log(event)

    val policy =
      policies.foldLeft(logAction) { case (current, policy) =>
        policy.apply(current)
      }

    config.logDeliveryFailure match {
      case Drop =>
        policy.handleErrorWith { _ =>
          IO.unit
        }

      case Stop =>
        policy
    }
  }

  private def drain(
      queue: Queue[IO, LogEvent],
      process: LogEvent => IO[Unit],
  ): IO[Unit] = {
    queue.tryTake.flatMap {
      case Some(event) =>
        process(event) *> drain(queue, process)

      case None =>
        IO.unit
    }
  }

  private def workerLoop(
      queue: Queue[IO, LogEvent],
      shutdown: Deferred[IO, Unit],
      process: LogEvent => IO[Unit],
  ): IO[Unit] = {

    queue.take.race(shutdown.get).flatMap {
      case Left(event) =>
        process(event) *> workerLoop(queue, shutdown, process)

      case Right(_) =>
        drain(queue, process)
    }
  }

  def createResource(
                      logSink: LogSink,
                      config: QueuedLogSinkConfig,
                      policies: LogDeliveryPolicy*,
                    ): Resource[IO, LogSink] = {

    for {
      queue <- Resource.eval(createQueue(config))

      workerDeath <- Resource.eval(
        Deferred[IO, Throwable],
      )

      shutdown <- Resource.eval(
        Deferred[IO, Unit],
      )

      worker <- Resource.make {

        val process: LogEvent => IO[Unit] =
          event =>
            applyPolicies(
              event,
              logSink,
              config,
              policies: _*,
            )

        workerLoop(
          queue,
          shutdown,
          process,
        ).handleErrorWith { error =>
          config.logDeliveryFailure match {
            case Stop =>
              workerDeath.complete(error).void

            case Drop =>
              IO.unit
          }
        }.start

      } { fiber =>
        shutdown.complete(()).void *>
          fiber.join.void
      }

    } yield new QueuedLogSink(
      queue = queue,
      config = config,
      workerDeath = workerDeath,
      shutdown = shutdown,
    )
  }
}
