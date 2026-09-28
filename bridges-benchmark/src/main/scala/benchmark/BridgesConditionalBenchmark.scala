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

package logger

import benchmark.BenchmarkBase
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import logEvent.LogEvent
import logEvent.LogLevel
import logSink.LogSink
import org.openjdk.jmh.annotations._

final class BridgesConditionalBenchmarkNoOpSink extends LogSink {
  override def log(event: LogEvent): IO[Unit] =
    IO.unit
}

class BridgesConditionalBenchmark extends BenchmarkBase {

  private val sink = new BridgesConditionalBenchmarkNoOpSink

  private val logger: BridgeLogger =
    BridgeLogger
      .builder()
      .withMinLevel(LogLevel.Info)
      .replayAllLogLevel(LogLevel.Error)
      .duplicateEntriesOnBufferDump(false)
      .sampleRate(1.0f)
      .sampleBelowMinLevel(false)
      .bufferBelowMinLevel(true)
      .build(sink)
      .unsafeRunSync()

  private val noBufferLogger: BridgeLogger =
    BridgeLogger
      .builder()
      .withMinLevel(LogLevel.Info)
      .replayAllLogLevel(LogLevel.Error)
      .duplicateEntriesOnBufferDump(false)
      .sampleRate(1.0f)
      .sampleBelowMinLevel(false)
      .bufferBelowMinLevel(false)
      .build(sink)
      .unsafeRunSync()

  private val message = "hello"

  private def run[A](fa: IO[A]): Unit =
    fa.unsafeRunSync()

  @Benchmark
  def bridgesWithRequestEmpty(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        IO.unit
      }()
    }

  @Benchmark
  def bridgesWithRequestConditionalSuccess(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        logger.debug(message) >>
          logger.debug(message) >>
          logger.info(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestConditionalError(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        logger.debug(message) >>
          logger.debug(message) >>
          logger.error(message)
      }()
    }

  @Benchmark
  def conditionalSuccess(): Unit =
    run {
      logger.debug("hello") >>
        logger.debug("hello") >>
        logger.info("hello")
    }

  @Benchmark
  def conditionalError(): Unit =
    run {
      logger.debug("hello") >>
        logger.debug("hello") >>
        logger.error("hello")
    }

  @Benchmark
  def bridgesWithRequestNoBuffer(): Unit =
    run {
      noBufferLogger.withRequest() {
        (1 to 25).foldLeft(IO.unit) { (acc, _) =>
          acc >> noBufferLogger.debug(message)
        } >> noBufferLogger.error(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale10NoError(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 10).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        }
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale25NoError(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 25).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        }
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale50NoError(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 50).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        }
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale100NoError(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 100).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        }
      }()
    }

  @Benchmark
  def bridgesWithRequestNaturalSampling(): Unit =
    run {
      logger.withRequest() {
        logger.debug(message) >>
          logger.debug(message) >>
          logger.info(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale10(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 10).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        } >> logger.error(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale25(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 25).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        } >> logger.error(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale50(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 50).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        } >> logger.error(message)
      }()
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScale100(): Unit =
    run {
      logger.withRequest(sampleRequest = Some(true)) {
        (1 to 100).foldLeft(IO.unit) { (acc, _) =>
          acc >> logger.debug(message)
        } >> logger.error(message)
      }()
    }

  private def noop: IO[Unit] = IO.unit

  @Benchmark
  def bridgesWithRequestBufferReplayAtScaleIOchain10(): Unit =
    run {
      (1 to 10).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      }
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScaleIOchain25(): Unit =
    run {
      (1 to 25).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      }
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScaleIOchain50(): Unit =
    run {
      (1 to 50).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      }
    }

  @Benchmark
  def bridgesWithRequestBufferReplayAtScaleIOchain100(): Unit =
    run {
      (1 to 100).foldLeft(IO.unit) { (acc, _) =>
        acc >> logger.debug(message)
      }
    }
}
