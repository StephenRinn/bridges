package logger

import logEvent.LogLevel
import logEvent.LogLevel.Info

case class BridgeLoggerConfig(
    sampleRate: Float = 1.0f,
    sampleBelowMinLevel: Boolean = true,
    bufferBelowMinLevel: Boolean = false,
    minLevel: LogLevel = Info,
)

object BridgeLoggerConfig {
  def default: BridgeLoggerConfig = {
    new BridgeLoggerConfig()
  }
}
