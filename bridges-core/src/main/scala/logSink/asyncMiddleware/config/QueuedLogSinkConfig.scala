package logSink.asyncMiddleware.config


import logSink.asyncMiddleware.config.LogDeliveryFailure.Drop
import logSink.asyncMiddleware.config.QueueCapacity.Unbounded
import logSink.asyncMiddleware.config.QueueOverflow.Block

case class QueuedLogSinkConfig (
    capacity: QueueCapacity = Unbounded,
    overflow: QueueOverflow = Block,
    logDeliveryFailure: LogDeliveryFailure = Drop,
                               )
