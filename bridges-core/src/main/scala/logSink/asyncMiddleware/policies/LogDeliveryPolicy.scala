package logSink.asyncMiddleware.policies

import cats.effect.IO

trait LogDeliveryPolicy {
  def apply[A](fa: => IO[A]): IO[A]
}
