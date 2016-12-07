package shade.benchmarks


import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import shade.memcached.{Configuration, FailureMode, Memcached, Protocol}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

/**
  * Base class for benchmarks that need an instance of [[Memcached]]
  */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
abstract class MemcachedBase {

  val memcached: Memcached = {
    val defaultConfig = Configuration(
      addresses = "127.0.0.1:11211",
      authentication = None,
      keysPrefix = Some("my-benchmarks"),
      protocol = Protocol.Binary,
      failureMode = FailureMode.Retry,
      operationTimeout = 15.seconds
    )
    Memcached(defaultConfig)(global)
  }

}
