/*
 * Copyright (c) 2012-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://github.com/monix/shade
 *
 * Licensed under the MIT License (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 * https://github.com/monix/shade/blob/master/LICENSE.txt
 */

package shade

/**
 * Super-class for errors thrown when specific cache-store related
 * errors occur.
 */
class CacheException(val msg: String) extends RuntimeException(msg)

/**
 * Thrown in case a cache store related operation times out.
 */
class TimeoutException(val key: String) extends CacheException(key)

/**
 * Thrown in case a cache store related operation is cancelled
 * (like due to closed / broken connections)
 */
class CancelledException(val key: String) extends CacheException(key)

/**
 * Gets thrown in case the implementation is wrong and
 * mishandled a status. Should never get thrown and
 * if it does, then it's a bug.
 */
class UnhandledStatusException(msg: String) extends CacheException(msg)

/**
 * Gets thrown in case a key is not found in the cache store on #apply().
 */
class KeyNotInCacheException(val key: String) extends CacheException(key)
