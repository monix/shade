package shade.tests

import shade.memcached._
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import shade.memcached.Configuration
import scala.Some

trait MemcachedTestHelpers extends MemcachedCodecs {
  val defaultConfig = Configuration(
    addresses = "127.0.0.1:11211",
    authentication = None,
    keysPrefix = Some("my-tests"),
    protocol = Protocol.Binary,
    failureMode = FailureMode.Retry,
    operationTimeout = 15.seconds
  )

  def createCacheObject(system: ActorSystem, prefix: String, opTimeout: Option[FiniteDuration] = None, failureMode: Option[FailureMode.Value] = None, isFake: Boolean = false): Memcached = {
    val config = defaultConfig.copy(
      keysPrefix = defaultConfig.keysPrefix.map(s => s + "-" + prefix),
      failureMode = failureMode.getOrElse(defaultConfig.failureMode),
      operationTimeout = opTimeout.getOrElse(defaultConfig.operationTimeout)
    )

    Memcached(config, system.scheduler, global)
  }

  def withFakeMemcached[T](cb: Memcached => T): T = {
    val cache = new FakeMemcached
    try {
      cb(cache)
    }
    finally {
      cache.shutdown()
    }
  }

  def withCache[T](prefix: String, failureMode: Option[FailureMode.Value] = None, opTimeout: Option[FiniteDuration] = None)(cb: Memcached => T): T = {
    val system = ActorSystem("memcached-test")
    val cache = createCacheObject(system = system, prefix = prefix, failureMode = failureMode, opTimeout = opTimeout)

    try {
      cb(cache)
    }
    finally {
      cache.shutdown()
    }
  }
}
