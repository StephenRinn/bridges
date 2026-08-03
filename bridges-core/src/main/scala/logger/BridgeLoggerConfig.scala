package logger

import logEvent.LogLevel
import logEvent.LogLevel.Info

case class BridgeLoggerConfig(
    minLevel: LogLevel = Info,
    sampleRate: Float = 1.0f,
    sampleBelowMinLevel: Boolean = false,
    bufferBelowMinLevel: Boolean = false,
    bufferSize: Int = 200,
)

object BridgeLoggerConfig {
  def default: BridgeLoggerConfig = {
    new BridgeLoggerConfig()
  }
}
