/*
 * Copyright 2026 Stephen Rinn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package logEvent

trait ToLogValue[A] {
  def toLogValue(value: A): LogValue

  def contraMap[B](f: B => A): ToLogValue[B] = {
    (value: B) => toLogValue(f(value))
  }
}

object ToLogValue {
  def apply[A](implicit ev: ToLogValue[A]): ToLogValue[A] =
    ev

  def instance[A](
      f: A => LogValue
                 ): ToLogValue[A] = {
    (value: A) => f(value)
  }

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

  implicit def optionToLogValue[A: ToLogValue]: ToLogValue[Option[A]] =
    {
      case Some(a) => ToLogValue[A].toLogValue(a)
      case None => LogValue.Null
    }

  implicit def listToLogValue[A: ToLogValue]: ToLogValue[List[A]] =
    value =>
      LogValue.ListValue(
        value.iterator.map(ToLogValue[A].toLogValue).toVector
      )

  implicit def vectorToLogValue[A: ToLogValue]: ToLogValue[Vector[A]] =
    value =>
      LogValue.ListValue(
        value.iterator.map(ToLogValue[A].toLogValue).toVector
      )

  implicit def mapToLogValue[A: ToLogValue]: ToLogValue[Map[String,A]] =
    value =>
      LogValue.MapValue(
        value.iterator.map { case (key, a) =>
          key -> ToLogValue[A].toLogValue(a)
        }.toMap
      )
}
