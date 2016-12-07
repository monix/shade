package shade.benchmarks

import org.openjdk.jmh.annotations.{Benchmark, Setup}
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent.duration._

class ExistingKeyOps extends MemcachedBase {

  val key: String = "existing"
  val duration: FiniteDuration = 1.day

  @Setup
  def prepare(): Unit = {
    memcached.set(key, 10L, duration)
  }

  @Benchmark
  def get(bh: Blackhole): Unit = bh.consume {
    memcached.awaitGet[String](key)
  }

  @Benchmark
  def set(bh: Blackhole): Unit =  bh.consume{
    memcached.awaitSet(key, 100L, duration)
  }

  @Benchmark
  def delete(bh: Blackhole): Unit = bh.consume {
    memcached.awaitDelete(key)
  }

}