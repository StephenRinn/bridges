package logSink.asyncMiddleware.config

trait QueueOverflow

object QueueOverflow {
  case object Block extends QueueOverflow
  case object DropNewest extends QueueOverflow
  case object DropOldest extends QueueOverflow
}