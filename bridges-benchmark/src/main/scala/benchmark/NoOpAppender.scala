package benchmark

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

class NoOpAppender extends AppenderBase[ILoggingEvent] {
  override def append(event: ILoggingEvent): Unit = ()
}
