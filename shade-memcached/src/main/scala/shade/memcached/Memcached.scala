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

package shade.memcached

import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/**
  * @define addDesc Adds a value for a given key, if the key doesn't
  *         already exist in the cache store.
  *
  *         If the key already exists in the cache, the returned
  *         result will be `false` and the current value will not be
  *         overridden. If the key isn't there already, the value will
  *         be set and the returned result will be `true`.
  *
  * @define addReturnDesc either `true`, in case the key was created,
  *         with the given value, or `false` in case the key already
  *         exists
  *
  * @define setDesc Stores a (key, value) pair in the cache store.
  *         If the `key` doesn't exist, then one is created. If
  *         the `key` exists, then it is updated.
  *
  * @define deleteDesc Deletes a `key` from the cache store.
  *
  * @define getDesc Fetches a value from the cache store associated
  *         with the given `key`.
  *
  * @define getsDesc Fetches a value from the cache store associated
  *         with the given `key` and also return its associated
  *         "cas ID" to use in `compareAndSet` operations.
  *
  * @define casDesc Atomic compare and set.
  *
  * @define rawCasDesc Atomic compare and set using cas IDs
  *         (fetched with `gets`).
  *
  * @define transformAndGetDesc Transforms the given key and
  *         returns the new value.
  *
  *         The given function receives the current value
  *         (`None` in case the key is missing or `Some(value)` otherwise)
  *         and should return the new value that was eventually stored.
  *
  *         The method goes into a `compareAndSet` loop until the cas operation
  *         succeeds, so the callback should have no side-effects.
  *
  * @define incrementDesc Atomic increment.
  *                       
  *         Increments the value stored with the given `key` by
  *         the given amount. If the `key` does not exist, then
  *         it creates it with the `default` value.
  *
  * @define casReturn either `true` (in case the compare-and-set
  *         operation succeeded) or `false` if not, in which case
  *         a concurrent operation probably happened
  *
  * @define getReturnDesc `Some(value)` in case the `key` is available,
  *         or `None` otherwise (doesn't throw exception on missing keys)
  *
  * @define getsReturnDesc `Some(CASValue(value, casId))` in case the
  *         `key` is available, or `None` otherwise (doesn't throw
  *         exception on missing keys)
  *
  * @define deleteReturnDesc `true` if a key was deleted or `false`
  *         if there was nothing there to delete
  *
  * @define codecParamDesc is the serializer and deserializer needed
  *         for storing the given `value`
  *
  * @define expParamDesc specifies the expiry time, can be infinite
  *         (`Duration.Inf`)
  *
  * @define keyUpdateParamDesc is the key in memcached to update
  *
  * @define valueParamDesc is the cached value, associated with the
  *         given `key`
  *
  * @define ecParamDesc is the `ExecutionContext` used to schedule
  *         asynchronous computations
  *
  * @define casCurrentParamDesc is the current value associated with
  *         the given `key`, should be `None` in case it should be
  *         missing, or `Some(value)` otherwise
  *
  * @define casUpdateParamDesc is the value to be associated with
  *         the given `key` if this operation succeeds
  *
  * @define casIdParamDesc is the value Id returned by `gets`
  */
abstract class Memcached extends java.io.Closeable {
  /** $addDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.add]] for the `Future`-enabled version
    *
    * @return a `Task` that on evaluation will signal
    *         $addReturnDesc
    */
  def addL[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Task[Boolean]

  /** $addDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    * @param ec $ecParamDesc
    *
    * @see [[Memcached.addL]] for the `Task`-enabled version
    *
    * @return a `CancelableFuture` that will signal
    *         $addReturnDesc
    */
  def add[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean]

  /** $setDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.set]] for the `Future`-enabled version
    */
  def setL[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Task[Unit]

  /** $setDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    * @param ec $ecParamDesc
    *
    * @see [[Memcached.setL]] for the `Task`-enabled version
    */
  def set[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Unit]

  /** $deleteDesc
    *
    * @see [[Memcached.delete]] for the `Future`-enabled version
    *
    * @param key is the key to delete if it exists
    * @return $deleteReturnDesc
    */
  def deleteL(key: String): Task[Boolean]

  /** $deleteDesc
    *
    * @see [[Memcached.deleteL]] for the `Task`-enabled version
    *
    * @param key is the key to delete if it exists
    * @return $deleteReturnDesc
    */
  def delete(key: String)(implicit ec: ExecutionContext): CancelableFuture[Boolean]

  /** $getDesc
    *
    * @see [[Memcached.get]] for the `Future`-enabled version
    *
    * @param key is the key whose value we need to fetch
    * @param codec $codecParamDesc
    *
    * @return $getReturnDesc
    */
  def getL[T](key: String)(implicit codec: Codec[T]): Task[Option[T]]

  /** $getDesc
    *
    * @see [[Memcached.getL]] for the `Task`-enabled version
    *
    * @param key is the key whose value we need to fetch
    * @param codec $codecParamDesc
    * @param ec $ecParamDesc
    *
    * @return $getReturnDesc
    */
  def get[T](key: String)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[T]]

  /** $getsDesc
    *
    * @see [[Memcached.gets]] for the `Future`-enabled version
    *
    * @param key is the key whose value we need to fetch
    * @param codec $codecParamDesc
    *
    * @return $getsReturnDesc
    */
  def getsL[T](key: String)(implicit codec: Codec[T]): Task[Option[CASValue[T]]]

  /** $getsDesc
    *
    * @see [[Memcached.getsL]] for the `Task`-enabled version
    *
    * @param key is the key whose value we need to fetch
    * @param codec $codecParamDesc
    * @param ec $ecParamDesc
    *
    * @return $getsReturnDesc
    */
  def gets[T](key: String)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[CASValue[T]]]

  /** $rawCasDesc
    *
    * @param key $keyUpdateParamDesc
    * @param casId $casIdParamDesc
    * @param update $casUpdateParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.rawCompareAndSet]] for the `Future`-enabled version
    *
    * @return $casReturn
    */
  def rawCompareAndSetL[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T]): Task[Boolean]

  /** $rawCasDesc
    *
    * @param key $keyUpdateParamDesc
    * @param casId $casIdParamDesc
    * @param update $casUpdateParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.rawCompareAndSetL]] for the `Task`-enabled version
    *
    * @return $casReturn
    */
  def rawCompareAndSet[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean]

  /** $casDesc
    *
    * @param key $keyUpdateParamDesc
    * @param current $casCurrentParamDesc
    * @param update $casUpdateParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.compareAndSet]] for the `Future`-enabled version
    *
    * @return $casReturn
    */
  def compareAndSetL[T](key: String, current: Option[T], update: T, exp: Duration)
    (implicit codec: Codec[T]): Task[Boolean] = {

    current match {
      case None => addL(key, update, exp)
      case Some(expected) =>
        getsL[T](key).flatMap {
          case Some(r) if r.getValue == expected =>
            rawCompareAndSetL[T](key, r.getCas, update, exp)
          case _ =>
            Task.now(false)
        }
    }
  }

  /** $casDesc
    *
    * @param key $keyUpdateParamDesc
    * @param current $casCurrentParamDesc
    * @param update $casUpdateParamDesc
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[Memcached.compareAndSetL]] for the `Task`-enabled version
    *
    * @return $casReturn
    */
  def compareAndSet[T](key: String, current: Option[T], update: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean] = {

    current match {
      case None => add(key, update, exp)
      case Some(expected) =>
        gets[T](key).flatMap {
          case Some(r) if r.getValue == expected =>
            rawCompareAndSet[T](key, r.getCas, update, exp)
          case _ =>
            CancelableFuture.successful(false)
        }
    }
  }

  /** $transformAndGetDesc
    *
    * @param key $keyUpdateParamDesc
    * @param f is the function that transforms the current value
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[transformAndGet]] for the `Future`-enabled function.
    *
    * @return the updated value
    */
  def transformAndGetL[T](key: String, exp: Duration)(f: Option[T] => T)
    (implicit codec: Codec[T]): Task[T] = {

    getsL[T](key).flatMap {
      case None =>
        val update = f(None)
        addL(key, update, exp).flatMap {
          case false => transformAndGetL(key, exp)(f)
          case true => Task.now(update)
        }
      case Some(r) =>
        val update = f(Option(r.getValue))
        rawCompareAndSetL(key, r.getCas, update, exp).flatMap {
          case false => transformAndGetL(key, exp)(f)
          case true => Task.now(update)
        }
    }
  }

  /** $transformAndGetDesc
    *
    * @param key $keyUpdateParamDesc
    * @param f is the function that transforms the current value
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[transformAndGetL]] for the `Task`-enabled function.
    *
    * @return the updated value
    */
  def transformAndGet[T](key: String, exp: Duration)(f: Option[T] => T)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[T] = {

    gets[T](key).flatMap {
      case None =>
        val update = f(None)
        add(key, update, exp).flatMap {
          case false => transformAndGet(key, exp)(f)
          case true => CancelableFuture.successful(update)
        }
      case Some(r) =>
        val update = f(Option(r.getValue))
        rawCompareAndSet(key, r.getCas, update, exp).flatMap {
          case false => transformAndGet(key, exp)(f)
          case true => CancelableFuture.successful(update)
        }
    }
  }
  
  //--
  /** $getAndTransformDesc
    *
    * @param key $keyUpdateParamDesc
    * @param f is the function that transforms the current value
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[getAndTransform]] for the `Future`-enabled function.
    *
    * @return the updated value
    */
  def getAndTransformL[T](key: String, exp: Duration)(f: Option[T] => T)
    (implicit codec: Codec[T]): Task[Option[T]] = {

    getsL[T](key).flatMap {
      case None =>
        val update = f(None)
        addL(key, update, exp).flatMap {
          case false => getAndTransformL(key, exp)(f)
          case true => Task.now(None)
        }
      case Some(r) =>
        val current = Option(r.getValue)
        val update = f(current)
        rawCompareAndSetL(key, r.getCas, update, exp).flatMap {
          case false => getAndTransformL(key, exp)(f)
          case true => Task.now(current)
        }
    }
  }

  /** $getAndTransformDesc
    *
    * @param key $keyUpdateParamDesc
    * @param f is the function that transforms the current value
    * @param exp $expParamDesc
    * @param codec $codecParamDesc
    *
    * @see [[getAndTransformL]] for the `Task`-enabled function.
    *
    * @return the updated value
    */
  def getAndTransform[T](key: String, exp: Duration)(f: Option[T] => T)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[T]] = {

    gets[T](key).flatMap {
      case None =>
        val update = f(None)
        add(key, update, exp).flatMap {
          case false => getAndTransform(key, exp)(f)
          case true => CancelableFuture.successful(None)
        }
      case Some(r) =>
        val current = Option(r.getValue)
        val update = f(current)
        rawCompareAndSet(key, r.getCas, update, exp).flatMap {
          case false => getAndTransform(key, exp)(f)
          case true => CancelableFuture.successful(current)
        }
    }
  }

  /** $incrementDesc
    *
    * @param key $keyUpdateParamDesc
    * @param by is the value to add
    * @param default is the default value to create in case the key is missing
    * @param exp $expParamDesc
    *
    * @see [[incrementAndGet]] for the `Future`-enabled version
    *
    * @return the incremented value or -1 if the increment failed
    */
  def incrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long]

  /** $incrementDesc
    *
    * @param key $keyUpdateParamDesc
    * @param by is the value to add
    * @param default is the default value to create in case the key is missing
    * @param exp $expParamDesc
    *
    * @see [[incrementAndGetL]] for the `Task`-enabled version
    *
    * @return the incremented value or -1 if the increment failed
    */
  def incrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long]

  /** $decrementDesc
    *
    * @param key $keyUpdateParamDesc
    * @param by is the value to add
    * @param default is the default value to create in case the key is missing
    * @param exp $expParamDesc
    *
    * @see [[decrementAndGet]] for the `Future`-enabled version
    *
    * @return the decremented value or -1 if the decrement failed
    */
  def decrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long]

  /** $decrementDesc
    *
    * @param key $keyUpdateParamDesc
    * @param by is the value to add
    * @param default is the default value to create in case the key is missing
    * @param exp $expParamDesc
    *
    * @see [[decrementAndGetL]] for the `Task`-enabled version
    *
    * @return the decremented value or -1 if the decrement failed
    */
  def decrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long]

  /** Shuts down the cache instance, performs any additional
    * cleanups necessary.
    */
  def close(): Unit
}

object Memcached {
  /**
    * Builds a [[Memcached]] instance. Needs a [[Configuration]].
    */
  def apply(config: Configuration): Memcached =
    new SpyMemcached(config)

  /** Returns a [[FakeMemcached]] implementation, useful for usage in tests. */
  def fake(implicit s: Scheduler): Memcached =
    new FakeMemcached(s)

  /** Extra extensions for [[Memcached]] */
  implicit class Extensions(val client: Memcached) extends AnyVal {
    /** Performs a [[Memcached.add]] and blocks for the result. */
    def awaitAdd[T](key: String, value: T, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit codec: Codec[T], ec: ExecutionContext): Boolean =
      Await.result(client.add(key, value, exp)(codec, ec), awaitAtMost)

    /** Performs a [[Memcached.set]] and blocks for the result. */
    def awaitSet[T](key: String, value: T, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit codec: Codec[T], ec: ExecutionContext): Unit =
      Await.result(client.set(key, value, exp)(codec, ec), awaitAtMost)

    /** Performs a [[Memcached.delete]] and blocks for the result. */
    def awaitDelete(key: String, awaitAtMost: Duration = Duration.Inf)
      (implicit ec: ExecutionContext): Boolean =
      Await.result(client.delete(key)(ec), awaitAtMost)

    /** Performs a [[Memcached.get]] and blocks for the result. */
    def awaitGet[T](key: String, awaitAtMost: Duration = Duration.Inf)
      (implicit codec: Codec[T], ec: ExecutionContext): Option[T] =
      Await.result(client.get(key)(codec, ec), awaitAtMost)

    /** Performs a [[Memcached.rawCompareAndSet]] and blocks for the result. */
    def awaitRawCompareAndSet[T](key: String, casId: Int, update: T, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit codec: Codec[T], ec: ExecutionContext): Boolean =
      Await.result(client.rawCompareAndSet(key, casId, update, exp)(codec, ec), awaitAtMost)

    /** Performs a [[Memcached.rawCompareAndSet]] and blocks for the result. */
    def awaitCompareAndSet[T](key: String, current: Option[T], update: T, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit codec: Codec[T], ec: ExecutionContext): Boolean =
      Await.result(client.compareAndSet(key, current, update, exp)(codec, ec), awaitAtMost)

    /** Performs a [[Memcached.transformAndGet]] and blocks for the result. */
    def awaitTransformAndGet[T](key: String, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (f: Option[T] => T)(implicit codec: Codec[T], ec: ExecutionContext): T =
      Await.result(client.transformAndGet(key, exp)(f), awaitAtMost)
    
    /** Performs a [[Memcached.getAndTransform]] and blocks for the result. */
    def awaitGetAndTransform[T](key: String, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (f: Option[T] => T)(implicit codec: Codec[T], ec: ExecutionContext): Option[T] =
      Await.result(client.getAndTransform(key, exp)(f), awaitAtMost)

    /** Performs an [[Memcached.incrementAndGet]] and blocks for the result. */
    def awaitIncrementAndGet(key: String, by: Long, default: Long, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit ec: ExecutionContext): Long =
      Await.result(client.incrementAndGet(key, by, default, exp), awaitAtMost)

    /** Performs an [[Memcached.decrementAndGet]] and blocks for the result. */
    def awaitDecrementAndGet(key: String, by: Long, default: Long, exp: Duration, awaitAtMost: Duration = Duration.Inf)
      (implicit ec: ExecutionContext): Long =
      Await.result(client.decrementAndGet(key, by, default, exp), awaitAtMost)
  }
}