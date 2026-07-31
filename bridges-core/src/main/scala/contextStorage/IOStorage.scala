package contextStorage

final case class IOStorage(
    requestId: String,
    correlationId: String,
    values: Map[String, String],
    startTime: Option[Long],
    endTime: Option[Long],
)

object IOStorage {
  val empty: IOStorage =
    IOStorage("","", Map[String, String](), None, None)
}
