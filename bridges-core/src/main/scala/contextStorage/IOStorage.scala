package contextStorage

import logEvent._

final case class IOStorage(
    requestId: String,
    correlationId: String,
    values: Map[String, String],
    startTime: Option[Long],
    endTime: Option[Long],
    sampled: Option[Boolean],
    rebuildLog: List[RebuildLog],
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
      sampled = None,
      List[RebuildLog]().empty,
    )
}
