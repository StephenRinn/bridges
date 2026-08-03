package logEvent

sealed trait LogLevel {
  val level: Int
}
object LogLevel {
  case object Trace extends LogLevel {
    val level = 0
  }
  case object Debug extends LogLevel {
    val level = 1
  }
  case object Info extends LogLevel {
    val level = 2
  }
  case object Warn extends LogLevel {
    val level = 3
  }
  case object Error extends LogLevel {
    val level = 4
  }

  implicit val levelOrdering: Ordering[LogLevel] = Ordering.by(_.level)
}
