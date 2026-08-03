package runtime

import contextStorage.ContextOperations
import logSink.LogSink
import logger.BridgeLoggerConfig

case class BridgeRuntime (
    context: ContextOperations,
    sink: LogSink,
    config: BridgeLoggerConfig
                         )
