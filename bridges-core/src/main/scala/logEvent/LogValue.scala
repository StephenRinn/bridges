package logEvent

trait LogValue

object LogValue {

  case object Null extends LogValue

  final case class StringValue(value: String) extends LogValue {
    override def toString: String = value.toString
  }
  final case class BooleanValue(value: Boolean) extends LogValue {
    override def toString: String = value.toString
  }
  final case class IntValue(value: Int) extends LogValue {
    override def toString: String = value.toString
  }
  final case class LongValue(value: Long) extends LogValue {
    override def toString: String = value.toString
  }
  final case class DoubleValue(value: Double) extends LogValue {
    override def toString: String = value.toString
  }

  final case class ListValue(values: List[LogValue]) extends LogValue {
    override def toString: String = values.toString
  }
  final case class MapValue(values: Map[String, LogValue]) extends LogValue {
    override def toString: String = values.toString
  }
}
