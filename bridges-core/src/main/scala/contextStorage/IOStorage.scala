package contextStorage

import logEvent._

final case class IOStorage(
    requestId: String,
    correlationId: String,
    values: Map[String, String],
    startTime: Option[Long],
    endTime: Option[Long],
    sampleData: SampleData,
    rebuildLog: List[RebuildLog],
)

final case class SampleData(
    sampleRate: Option[Float],
    sampleVal: Option[Float],
)

final case class RebuildLog(
    log: LogEvent,
    level: LogLevel,
)

object IOStorage {
  val empty: IOStorage =
    IOStorage(
      "",
      "",
      Map[String, String](),
      None,
      None,
      SampleData(None, None),
      List[RebuildLog]().empty,
    )
}
