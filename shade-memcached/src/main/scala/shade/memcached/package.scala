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

package object memcached {
  /** A byte array with flags attached, as stored in Memcached.
    *
    * Used by [[Codec]] to encode and decode data.
    */
  type CachedData = net.spy.memcached.CachedData

  /** A value with a cas identifier attached. */
  type CASValue[A] = net.spy.memcached.CASValue[A]
}
