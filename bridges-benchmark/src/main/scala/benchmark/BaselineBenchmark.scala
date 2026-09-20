package benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.Benchmark

class BaselineBenchmark extends BenchmarkBase {

  @Benchmark
  def empty(): Unit =
    ()

  @Benchmark
  def ioBaseline(): Unit =
    IO.unit.unsafeRunSync()
}