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

package shade.local.immutable

import shade.local.immutable.TimeBasedCache.{Timestamp, Value}
import scala.annotation.tailrec
import scala.collection.immutable.SortedMap
import scala.concurrent.duration._

/** Describes an immutable cache data-structure.
  *
  * It behaves much like a standard `scala.collection.immutable.Map`, but
  * the values have an expiration timestamp attached. So the cached values
  * might become unavailable depending on the current time, explicitly
  * specified as `now` in the operations that need it.
  *
  * Example:
  * {{{
  *   import scala.concurrent.duration._
  *   import shade.local.immutable.TimeBasedCache
  *
  *   val now = System.currentTimeMillis()
  *
  *   val cache = TimeBasedCache.empty[String]
  *     .set("key1", "value1", 1.minute, now)
  *     .set("key2", "value2", 1.minute, now)
  *
  *   cache.get("key1", now)
  *   //=> Some("value1")
  *
  *   cache.get("key1", now + 1.minute.toMillis)
  *   //=> None
  * }}}
  *
  * @param keysToValues is a map that keeps the cached key and value tuples,
  *        where the [[TimeBasedCache$.Value values]] have an expiry
  *        timestamp attached
  *
  * @param expiryOrder is a sorted sequence of timestamps to keys mapping
  *        that represent the order in which keys need to be expired from
  *        the cache, as an optimization when doing the cleanup
  *
  */
final case class TimeBasedCache[+A](
  keysToValues: Map[String, Value[A]],
  expiryOrder: SortedMap[Timestamp, Set[String]]) {

  /** Fetches the cached value associated with a given key,
    * returning `None` if the `key` does not exist in the cache,
    * or if it expired (relative to `now`).
    *
    * @param key is the associated key for the returned cached value
    * @param now is the current timestamp, used to determine if the
    *        cached value is expired or not
    *
    * @return `Some(value)` in case the value exists in the cache and
    *         isn't expired, or `None` otherwise
    */
  def get(key: String, now: Timestamp): Option[A] =
    keysToValues.get(key) match {
      case Some(r) if r.expiresAt > now => Some(r.value)
      case _ => None
    }

  /** Fetches the cached value associated with a given key,
    * returning the given `default` if the `key` does not exist in
    * the cache, or if it expired (relative to `now`).
    *
    * @param key is the associated key for the returned cached value
    * @param default is the value to return in case the given `key`
    *        doesn't exist, or the cached value is expired
    * @param now is the current timestamp, used to determine if the
    *        cached value is expired or not
    *
    * @return the cached value, in case the associated `key` exists
    *         and it isn't expired, or otherwise the `default`
    */
  def getOrElse[B >: A](key: String, default: B, now: Timestamp): B =
    keysToValues.get(key) match {
      case Some(r) if r.expiresAt > now => r.value
      case _ => default
    }

  /** Returns the number of non-expired keys in the cache. */
  def size(now: Timestamp): Int =
    keysToValues.count(_._2.expiresAt > now)

  /** Returns the number of keys in the cache, both active and expired. */
  def rawSize: Int =
    keysToValues.size

  /** Adds a new value to the cache, associated with the given `key`,
    * but only if the given `key` doesn't already exist in the cache.
    *
    * @param key is the key to associate with the given value
    * @param value is the value to persist in the cache
    * @param expiry is the duration after which the value is expired,
    *        can be infinite (e.g. `Duration.Inf`)
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        calculate the exact timestamp when the new value will
    *        be expired
    *
    * @return an `(isSuccess, newState)` tuple, signaling `true` if a
    *         new key was added to the cache, or `false` if no changes
    *         have been made due to the `key` already being present and
    *         its value being active
    */
  def add[B >: A](key: String, value: B, expiry: Duration, now: Timestamp): (Boolean, TimeBasedCache[B]) = {
    val ts = getExpiryTS(expiry, now)
    val oldRawValue = keysToValues.get(key)
    val itemExists = oldRawValue match {
      case Some(item) if item.expiresAt > now => true
      case _ => false
    }

    if (itemExists || ts <= now)
      (false, this)
    else
      (true, buildNewState(key, value, ts, oldRawValue))
  }

  /** Sets the given `key` to the given `value` in the cache.
    *
    * @param key is the key to associate with the given value
    * @param value is the value to persist in the cache
    * @param expiry is the duration after which the value is expired,
    *        can be infinite (e.g. `Duration.Inf`)
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        calculate the exact timestamp when the new value will
    *        be expired
    *
    * @return a new cache containing the given `value` associated
    *         with the given `key`
    */
  def set[B >: A](key: String, value: B, expiry: Duration, now: Timestamp): TimeBasedCache[B] = {
    val ts = getExpiryTS(expiry, now)
    buildNewState(key, value, ts, keysToValues.get(key))
  }

  /** Deletes a given `key` from the cache.
    *
    * @param key is the `key` to delete from the cache
    *
    * @return `(isSuccess, newState)` tuple, by which it signals `true`
    *         in case the `key` was present in the cache with an unexpired
    *         value, a key that was deleted, or `false` in case no such
    *         key was present and so nothing was deleted
    */
  def delete(key: String): (Boolean, TimeBasedCache[A]) =
    this.keysToValues.get(key) match {
      case Some(value) =>
        val newValues = this.keysToValues - key
        val newOrder = {
          val ts = value.expiresAt
          // If expiresAt is Inf, don't even bother to delete it
          // from expiryOrder, because it shouldn't be there!
          if (ts == Long.MaxValue) this.expiryOrder else {
            val set = this.expiryOrder.getOrElse(ts, Set.empty) - key
            if (set.isEmpty) this.expiryOrder - ts
            else this.expiryOrder.updated(ts, set)
          }
        }

        val state = TimeBasedCache(keysToValues=newValues, expiryOrder=newOrder)
        (true, state)

      case None =>
        (false, this)
    }

  /** Performs a compare and set operation, that updates the given `key`
    * only if the `expected` value is equal to the cached value.
    *
    * @param key is the `key` to be updated
    * @param expected is the value we expect to be in the cache, passed as
    *        `None` in case the given `key` shouldn't exist or if its associated
    *        value is expired
    * @param update is the value to be stored for the the given `key` in
    *        case of success
    * @param expiry is the duration after which the value is expired,
    *        can be infinite (e.g. `Duration.Inf`), to be used onlt
    *        in case the update is a success
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        test whether the current value associated to the given
    *        `key` is expired and also to calculate the exact timestamp
    *        when the new value will be expired
    *
    * @return either `true` in case the operation was a success, or
    *         `false` otherwise, along with the updated cache
    */
  def compareAndSet[B >: A](key: String, expected: Option[B], update: B,
    expiry: Duration, now: Timestamp): (Boolean, TimeBasedCache[B]) = {

    expected match {
      case None => add(key, update, expiry, now)
      case Some(expectedValue) =>
        keysToValues.get(key) match {
          case Some(r) if r.expiresAt > now =>
            if (r.value == expectedValue) {
              val ts = getExpiryTS(expiry, now)
              val newState = buildNewState(key, update, ts, keysToValues.get(key))
              (true, newState)
            } else {
              (false, this)
            }
          case _ =>
            (false, this)
        }
    }
  }

  /** Given a function, transforms and persists an update for
    * the value associated with the given `key`, returning the
    * updated value.
    *
    * The given function admits keys not already present in the
    * cache, or with values that are expired, thus receiving
    * `None` in such a case.
    *
    * @param key is the key that will have its associated value transformed
    * @param expiry is the duration after which the new value is expired,
    *        can be infinite (e.g. `Duration.Inf`)
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        calculate the exact timestamp when the new value will
    *        be expired
    * @param f is the transformation function, can receive `None` in
    *        case the `key` doesn't exist in the cache or if its value
    *        is expired
    *
    * @return the updated value along with the new cache
    */
  def transformAndGet[B >: A](key: String, expiry: Duration, now: Timestamp)
    (f: Option[A] => B): (B, TimeBasedCache[B]) = {

    val ts = getExpiryTS(expiry, now)
    val oldRawValue = keysToValues.get(key)
    val value = oldRawValue match {
      case Some(v) if v.expiresAt > now => Some(v.value)
      case _ => None
    }

    val newValue = f(value)
    val update = buildNewState(key, newValue, ts, oldRawValue)
    (newValue, update)
  }

  /** Given a function, transforms and persists an update for
    * the value associated with the given `key`, returning the
    * old value, prior to its update.
    *
    * The given function admits keys not already present in the
    * cache, or with values that are expired, thus receiving
    * `None` in such a case.
    *
    * @param key is the key that will have its associated value transformed
    * @param expiry is the duration after which the new value is expired,
    *        can be infinite (e.g. `Duration.Inf`)
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        calculate the exact timestamp when the new value will
    *        be expired
    * @param f is the transformation function, can receive `None` in
    *        case the `key` doesn't exist in the cache or if its value
    *        is expired
    *
    * @return the old value, prior to its update, along with the new cache
    */
  def getAndTransform[B >: A](key: String, expiry: Duration, now: Timestamp)
    (f: Option[A] => B): (Option[B], TimeBasedCache[B]) = {

    val ts = getExpiryTS(expiry, now)
    val oldRawValue = keysToValues.get(key)
    val value = oldRawValue match {
      case Some(v) if v.expiresAt > now => Some(v.value)
      case _ => None
    }

    val newValue = f(value)
    val update = buildNewState(key, newValue, ts, oldRawValue)
    (value, update)
  }

  /** Given a function, transforms and persists an update for
    * the value associated with the given `key`, returning an
    * extracted result.
    *
    * The given function admits keys not already present in the
    * cache, or with values that are expired, thus receiving
    * `None` in such a case.
    *
    * @param key is the key that will have its associated value transformed
    * @param expiry is the duration after which the new value is expired,
    *        can be infinite (e.g. `Duration.Inf`)
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        calculate the exact timestamp when the new value will
    *        be expired
    * @param f is the transformation function, can receive `None` in
    *        case the `key` doesn't exist in the cache or if its value
    *        is expired
    *
    * @return an extracted `R` value, along with the new cache
    */
  def transformAndExtract[B >: A, R](key: String, expiry: Duration, now: Timestamp)
    (f: Option[A] => (R,B)): (R, TimeBasedCache[B]) = {

    val ts = getExpiryTS(expiry, now)
    val oldRawValue = keysToValues.get(key)
    val value = oldRawValue match {
      case Some(v) if v.expiresAt > now => Some(v.value)
      case _ => None
    }

    val (extract, newValue) = f(value)
    val update = buildNewState(key, newValue, ts, oldRawValue)
    (extract, update)
  }

  /** Performs cleanup of the source cache, deleting keys that are expired,
    * relative to the given `now`.
    *
    * @param now is the current timestamp, given in milliseconds since
    *        the epoch (e.g. `System.currentTimeMillis`), used to
    *        determine which keys are expired
    *
    * @return the number of keys that have been deleted from the source
    *         cache, along with the new cache that has those keys deleted
    */
  def cleanse(now: Timestamp): (Int, TimeBasedCache[A]) = {
    @tailrec def loop(self: TimeBasedCache[A], now: Timestamp, acc: Int): (Int, TimeBasedCache[A]) = {
      val order = self.expiryOrder
      if (order.isEmpty) (acc, self) else {
        val (ts, keys) = order.head

        if (ts > now) (acc, self) else {
          val newOrder = order - ts
          val newMap = self.keysToValues -- keys
          val update = TimeBasedCache(keysToValues = newMap, expiryOrder = newOrder)
          loop(update, now, acc + keys.size)
        }
      }
    }

    loop(this, now, 0)
  }

  @inline
  private def getExpiryTS(expiry: Duration, now: Timestamp): Timestamp =
    if (expiry.isFinite()) now + expiry.toMillis
    else Long.MaxValue

  private def buildNewState[B >: A](key: String, value: B, ts: Timestamp, oldRawValue: Option[Value[B]]) = {
    val newValues = keysToValues.updated(key, Value(value, ts))

    // We might have a previous entry in the expiry order for
    // the given key, so we need to remove it
    val orderClean = oldRawValue match {
      case Some(v) if v.expiresAt != ts && v.expiresAt < Long.MaxValue =>
        this.expiryOrder.get(v.expiresAt) match {
          case None => this.expiryOrder
          case Some(set) =>
            val newSet = set - key
            if (newSet.isEmpty) this.expiryOrder - v.expiresAt
            else this.expiryOrder.updated(v.expiresAt, newSet)
        }
      case _ =>
        this.expiryOrder
    }

    // Building a new expiry order that includes the new timestamp.
    // With optimization for `Duration.Inf`.
    val newOrder =
      if (ts == Long.MaxValue) orderClean else {
        val collisionSet = orderClean.getOrElse(ts, Set.empty)
        orderClean.updated(ts, collisionSet + key)
      }

    TimeBasedCache(keysToValues = newValues, expiryOrder = newOrder)
  }
}

object TimeBasedCache {
  /** Returns an empty [[TimeBasedCache]] instance. */
  def empty[A]: TimeBasedCache[A] = emptyRef

  /** Returns an empty [[TimeBasedCache]] instance. */
  def apply[A](): TimeBasedCache[A] = emptyRef

  /** Using a type-alias for `Long`, describing Unix timestamps
    * specified in milliseconds since the epoch.
    */
  type Timestamp = Long

  /** Represents the stored values, having an `expiresAt`
    * timestamp attached, as a Unix timestamp, thus specified
    * in milliseconds since the epoch.
    */
  final case class Value[+A](value: A, expiresAt: Timestamp)

  // Empty reference reusable because of covariance.
  private[this] val emptyRef: TimeBasedCache[Nothing] =
    TimeBasedCache(Map.empty, SortedMap.empty)
}
