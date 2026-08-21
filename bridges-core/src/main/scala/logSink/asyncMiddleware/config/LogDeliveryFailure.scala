package logSink.asyncMiddleware.config

sealed trait LogDeliveryFailure


object LogDeliveryFailure {
  case object Drop extends LogDeliveryFailure
  case object Stop extends LogDeliveryFailure
}