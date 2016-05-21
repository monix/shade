package shade.memcached

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

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
   * Atomically increments the given key by a non-negative integer amount
   * and returns the new value.
   *
   * The value is stored as the ASCII decimal representation of a 64-bit
   * unsigned integer.
   *
   * If the key does not exist and a default is provided, sets the value of the
   * key to the provided default and expiry time.
   *
   * If the key does not exist and no default is provided, or if the key exists
   * with a value that does not conform to the expected representation, the
   * operation will fail.
   *
   * If the operation succeeds, it returns the new value of the key.
   *
   * Note that the default value is always treated as None when using the text
   * protocol.
   *
   * The expiry time can be Duration.Inf (infinite duration).
   */
  def increment(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long]

  def awaitIncrement(key: String, by: Long, default: Option[Long], exp: Duration): Long =
    Await.result(increment(key, by, default, exp), Duration.Inf)

  /**
   * Atomically decrements the given key by a non-negative integer amount
   * and returns the new value.
   *
   * The value is stored as the ASCII decimal representation of a 64-bit
   * unsigned integer.
   *
   * If the key does not exist and a default is provided, sets the value of the
   * key to the provided default and expiry time.
   *
   * If the key does not exist and no default is provided, or if the key exists
   * with a value that does not conform to the expected representation, the
   * operation will fail.
   *
   * If the operation succeeds, it returns the new value of the key.
   *
   * Note that the default value is always treated as None when using the text
   * protocol.
   *
   * The expiry time can be Duration.Inf (infinite duration).
   */
  def decrement(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long]

  def awaitDecrement(key: String, by: Long, default: Option[Long], exp: Duration): Long =
    Await.result(decrement(key, by, default, exp), Duration.Inf)

  /**
   * Shuts down the cache instance, performs any additional cleanups necessary.
   */
  def close(): Unit
}

object Memcached {
  /**
   * Builds a [[Memcached]] instance. Needs a [[Configuration]].
   */
  def apply(config: Configuration)(implicit ec: ExecutionContext): Memcached =
    new MemcachedImpl(config, ec)
}
