package logSink.asyncMiddleware.config

sealed trait QueueCapacity

object QueueCapacity {
  case object Unbounded extends QueueCapacity
  final case class Bounded(max: Int) extends QueueCapacity
}
