package shade.tests

import shade.memcached._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import shade.memcached.Configuration

trait MemcachedTestHelpers extends MemcachedCodecs {
  val defaultConfig = Configuration(
    addresses = "127.0.0.1:11211",
    authentication = None,
    keysPrefix = Some("my-tests"),
    protocol = Protocol.Binary,
    failureMode = FailureMode.Retry,
    operationTimeout = 15.seconds
  )

  def createCacheObject(prefix: String, opTimeout: Option[FiniteDuration] = None, failureMode: Option[FailureMode.Value] = None, isFake: Boolean = false): Memcached = {
    val config = defaultConfig.copy(
      keysPrefix = defaultConfig.keysPrefix.map(s => s + "-" + prefix),
      failureMode = failureMode.getOrElse(defaultConfig.failureMode),
      operationTimeout = opTimeout.getOrElse(defaultConfig.operationTimeout)
    )

    Memcached(config, global)
  }

  def withFakeMemcached[T](cb: Memcached => T): T = {
    val cache = new FakeMemcached(global)
    try {
      cb(cache)
    }
    finally {
      cache.close()
    }
  }

  def withCache[T](prefix: String, failureMode: Option[FailureMode.Value] = None, opTimeout: Option[FiniteDuration] = None)(cb: Memcached => T): T = {
    val cache = createCacheObject(prefix = prefix, failureMode = failureMode, opTimeout = opTimeout)

    try {
      cb(cache)
    }
    finally {
      cache.close()
    }
  }
}
