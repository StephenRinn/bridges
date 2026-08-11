package logEvent

trait ToLogValue[A] {
  def toLogValue(value: A): LogValue
}

object ToLogValue {
  def apply[A](implicit ev: ToLogValue[A]): ToLogValue[A] =
    ev

  implicit val stringToLogValue: ToLogValue[String] =
    value => LogValue.StringValue(value)

  implicit val booleanToLogValue: ToLogValue[Boolean] =
    value => LogValue.BooleanValue(value)

  implicit val intToLogValue: ToLogValue[Int] =
    value => LogValue.IntValue(value)

  implicit val longToLogValue: ToLogValue[Long] =
    value => LogValue.LongValue(value)

  implicit val doubleToLogValue: ToLogValue[Double] =
    value => LogValue.DoubleValue(value)

  implicit val logValueToLogValue: ToLogValue[LogValue] =
    identity
}
