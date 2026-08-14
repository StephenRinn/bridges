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

trait LogValue

object LogValue {

  case object Null extends LogValue

  final case class StringValue(value: String) extends LogValue {
    override def toString: String = value
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
  final case class ListValue(values: Vector[LogValue]) extends LogValue {
    override def toString: String = values.mkString("[", ", " , "]")
  }
  final case class MapValue(values: Map[String, LogValue]) extends LogValue {
    override def toString: String = values.toString
  }
  def obj(fields: (String, LogValue)*): LogValue = {
    MapValue(fields.toMap)
  }
  def array(values: LogValue*): LogValue = ListValue(values.toVector)
}
