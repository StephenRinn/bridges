/*
 * /*
 *  * Copyright 2026 Stephen Rinn
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  */
 */

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
