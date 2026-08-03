package logger

import logEvent.LogLevel
import logEvent.LogLevel.Info

case class BridgeLoggerConfig (
    sampleRate: Float = 1.0F,
    repopulateAll: Boolean = true,
    minLevel: LogLevel = Info
                              )


object BridgeLoggerConfig {
  def default: BridgeLoggerConfig = {
    new BridgeLoggerConfig()
  }
}