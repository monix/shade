package shade.benchmarks


import scala.concurrent.duration._
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

class IncDecrOps extends MemcachedBase {

  val key: String = "incr-decr"
  val duration: FiniteDuration = 1.day

  @Setup
  def prepare(): Unit = {
    memcached.awaitSet(key, 1E10.toLong.toString, duration)
  }

  @Benchmark
  def increment(bh: Blackhole): Unit = bh.consume{
    memcached.awaitIncrement(key, 1L, None, duration)
  }

  @Benchmark
  def decrement(bh: Blackhole): Unit = bh.consume {
    memcached.awaitDecrement(key, 1L, None, duration)
  }

}