package logger

import logEvent.LogLevel
import logEvent.LogLevel.Info

case class BridgeLoggerConfig (
    traceSampleRate: Float = 1.0F,
    debugSampleRate: Float = 1.0F,
    infoSampleRate: Float = 1.0F,
    warnSampleRate: Float = 1.0F,
    minLevel: LogLevel = Info
                              )


object BridgeLoggerConfig {
  def default: BridgeLoggerConfig = {
    new BridgeLoggerConfig()
  }
}