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

package logSink.asyncMiddleware.config

import logSink.asyncMiddleware.config.LogDeliveryFailure.Drop
import logSink.asyncMiddleware.config.QueueCapacity.Unbounded
import logSink.asyncMiddleware.config.QueueOverflow.Block

case class QueuedLogSinkConfig(
    capacity: QueueCapacity = Unbounded,
    overflow: QueueOverflow = Block,
    logDeliveryFailure: LogDeliveryFailure = Drop,
)
