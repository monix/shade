package shade.benchmarks

import scala.concurrent.duration._
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

class NonExistingKeyOps extends MemcachedBase {

  val key: String = "non-existing"
  val duration: FiniteDuration = 1.day

  @Setup
  def prepare(): Unit = memcached.delete(key)

  @Benchmark
  def get(bh: Blackhole): Unit = bh.consume {
    memcached.awaitGet[String](key)
  }

  @Benchmark
  def set(bh: Blackhole): Unit = bh.consume {
    memcached.awaitSet(key, 1L, duration)
  }

  @Benchmark
  def delete(bh: Blackhole): Unit = bh.consume {
    memcached.awaitDelete(key)
  }
}