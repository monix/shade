package shade.memcached

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait Memcached extends java.io.Closeable {
  /**
   * Adds a value for a given key, if the key doesn't already exist in the cache store.
   *
   * If the key already exists in the cache, the future returned result will be false and the
   * current value will not be overridden. If the key isn't there already, the value
   * will be set and the future returned result will be true.
   *
   * The expiry time can be Duration.Inf (infinite duration).
   *
   * @return either true, in case the value was set, or false otherwise
   */
  def add[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean]

  def awaitAdd[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Boolean =
    Await.result(add(key, value, exp), Duration.Inf)

  /**
   * Sets a (key, value) in the cache store.
   *
   * The expiry time can be Duration.Inf (infinite duration).
   */
  def set[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Unit]

  def awaitSet[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]) {
    Await.result(set(key, value, exp), Duration.Inf)
  }

  /**
   * Deletes a key from the cache store.
   *
   * @return true if a key was deleted or false if there was nothing there to delete
   */
  def delete(key: String): Future[Boolean]

  def awaitDelete(key: String) =
    Await.result(delete(key), Duration.Inf)

  /**
   * Fetches a value from the cache store.
   *
   * @return Some(value) in case the key is available, or None otherwise (doesn't throw exception on key missing)
   */
  def get[T](key: String)(implicit codec: Codec[T]): Future[Option[T]]

  def awaitGet[T](key: String)(implicit codec: Codec[T]): Option[T] =
    Await.result(get[T](key), Duration.Inf)

  /**
   * Compare and set.
   *
   * @param expecting should be None in case the key is not expected, or Some(value) otherwise
   * @param exp can be Duration.Inf (infinite) for not setting an expiration
   * @return either true (in case the compare-and-set succeeded) or false otherwise
   */
  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean]

  /**
   * Transforms the given key and returns the new value.
   *
   * The cb callback receives the current value
   * (None in case the key is missing or Some(value) otherwise)
   * and should return the new value to store.
   *
   * The method retries until the compare-and-set operation succeeds, so
   * the callback should have no side-effects.
   *
   * This function can be used for atomic increments and stuff like that.
   *
   * @return the new value
   */
  def transformAndGet[T](key: String, exp: Duration)(cb: Option[T] => T)(implicit codec: Codec[T]): Future[T]

  /**
   * Transforms the given key and returns the old value as an Option[T]
   * (None in case the key wasn't in the cache or Some(value) otherwise).
   *
   * The cb callback receives the current value
   * (None in case the key is missing or Some(value) otherwise)
   * and should return the new value to store.
   *
   * The method retries until the compare-and-set operation succeeds, so
   * the callback should have no side-effects.
   *
   * This function can be used for atomic increments and stuff like that.
   *
   * @return the old value
   */
  def getAndTransform[T](key: String, exp: Duration)(cb: Option[T] => T)(implicit codec: Codec[T]): Future[Option[T]]

  /**
   * Shuts down the cache instance, performs any additional cleanups necessary.
   */
  def close()
}

object Memcached {
  def apply(config: Configuration, ec: ExecutionContext): Memcached =
    new MemcachedImpl(config, ec)

  /**
   * Create new Memcached
   *
   * @param addresses         the list of server addresses, separated by space,
   *                          e.g. `"192.168.1.3:11211 192.168.1.4:11211"`
   * @param authentication    the authentication credentials (if None, then no authentication is performed)
   *
   * @param keysPrefix        is the prefix to be added to used keys when storing/retrieving values,
   *                          useful for having the same Memcached instances used by several
   *                          applications to prevent them from stepping over each other.
   *
   * @param protocol          can be either `Text` or `Binary`
   *
   * @param failureMode       specifies failure mode for SpyMemcached when connections drop:
   *                          - in Retry mode a connection is retried until it recovers.
   *                          - in Cancel mode all operations are cancelled
   *                          - in Redistribute mode, the client tries to redistribute operations to other nodes
   *
   * @param operationTimeout  is the default operation timeout; When the limit is reached, the
   *                          Future responses finish with Failure(TimeoutException)
   */
  def apply(
    addresses: String,
    authentication: Option[AuthConfiguration] = None,
    keysPrefix: Option[String] = None,
    protocol: Protocol.Value = Protocol.Binary,
    failureMode: FailureMode.Value = FailureMode.Retry,
    operationTimeout: FiniteDuration = 1.second
  )(implicit ec: ExecutionContext): Memcached = {
    val config = Configuration(addresses, authentication, keysPrefix, protocol, failureMode, operationTimeout)
    apply(config, ec)
  }
}
