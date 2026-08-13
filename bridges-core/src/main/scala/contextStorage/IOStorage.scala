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
