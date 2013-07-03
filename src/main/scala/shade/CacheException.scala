package shade

/**
 * Super-class for errors thrown when specific cache-store related
 * errors occur.
 */
class CacheException(msg: String) extends RuntimeException(msg)

/**
 * Thrown in case a cache store related operation times out.
 */
class TimeoutException(key: String) extends CacheException(key)

/**
 * Thrown in case a cache store related operation is cancelled
 * (like due to closed / broken connections)
 */
class CancelledException(key: String) extends CacheException(key)

/**
 * Gets thrown in case the implementation is wrong and
 * mishandled a status. Should never get thrown and
 * if it does, then it's a bug.
 */
class UnhandledStatusException(msg: String) extends CacheException(msg)

/**
 * Gets thrown in case a key is not found in the cache store on #apply().
 */
class KeyNotInCacheException(key: String) extends CacheException(key)

/**
 * Gets thrown in case a transform method (transformAndGet)
 * exceeds the maximum amount of retries configured.
 */
class TransformOverflowException(key: String) extends CacheException(key)