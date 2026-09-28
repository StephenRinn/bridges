package benchmark

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.openjdk.jmh.infra.Blackhole

class BlackholeAppender extends AppenderBase[ILoggingEvent] {
  override def append(event: ILoggingEvent): Unit = {
    Blackhole.consumeCPU(1)
  }
}
