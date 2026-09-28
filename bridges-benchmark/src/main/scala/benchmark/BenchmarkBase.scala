package benchmark

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
abstract class BenchmarkBase
