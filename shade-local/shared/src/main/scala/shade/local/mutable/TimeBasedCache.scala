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

package shade.local.mutable

import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import shade.local.Platform
import shade.local.immutable.{TimeBasedCache => ImmutableTimeBasedCache}
import shade.local.immutable.TimeBasedCache.Timestamp
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.math.ceil
import scala.util.control.NonFatal

/** Interface for mutable cache implementations.
  *
  * @define addDesc Atomically persists the given `(key, value)`
  *         in the cache, but only if the `key` doesn't exist.
  *
  * @define addReturnDesc `true` if there was no such `key` in the cache and
  *         the persistence took place, or `false` otherwise
  *
  * @define setDesc Atomically updates the specified `key` with the given `value`.
  *
  * @define cachedFutureDesc If the given `key` exists in the cache and isn't expired
  *         then returns its associated value, otherwise atomically
  *         executes the given `Future`, cache its result and return it.
  *
  * @define cachedTaskDesc If the given `key` exists in the cache and isn't expired
  *         then returns its associated value, otherwise creates a `Task`
  *         that upon evaluation will evaluate the given `Task`, store
  *         store its value in the cache and returns it
  *
  * @define cachedFutureReturn the value associated with the given `key`,
  *         or that was generated in case the `key` was missing from
  *         the cache
  *
  * @define keyUpdateParamDesc is the key in memcached to update
  *
  * @define valueParamDesc is the cached value, to associate with the
  *         given `key`
  *
  * @define expParamDesc specifies the expiry time, can be infinite
  *         (`Duration.Inf`)
  */
abstract class TimeBasedCache[A] extends AutoCloseable {
  /** Return the value associated with the given `key`.
    *
    * @return `Some(value)` in case there exists a `key` in
    *         the cache that isn't expired, or `None` otherwise
    */
  def get(key: String): Option[A]

  /** Return the value associated with the given `key`,
    * or a `default` in case the given `key` doesn't exist.
    *
    * In case the given `key` doesn't have an associated value,
    * or if it is expired, then the `default` by-name parameter
    * is returned instead.
    */
  def getOrElse(key: String, default: A): A

  /** $addDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param expiry $expParamDesc
    *
    * @return $addReturnDesc
    */
  def add(key: String, value: A, expiry: Duration): Boolean

  /** $setDesc
    *
    * @param key $keyUpdateParamDesc
    * @param value $valueParamDesc
    * @param expiry $expParamDesc
    */
  def set(key: String, value: A, expiry: Duration): Unit

  /** Deletes the given `key` from the cache.
    *
    * @return `true` if there was a `key` in the cache that was
    *         deleted, or `false` otherwise
    */
  def delete(key: String): Boolean

  /** $cachedFutureDesc
    *
    * @param key $keyUpdateParamDesc
    * @param cb is the callback to execute in case the `key` is missing
    * @param expiry $expParamDesc
    *
    * @return $cachedFutureReturn
    */
  def cachedFuture(key: String, expiry: Duration)
    (cb: Scheduler => Future[A]): Future[A]

  /** $cachedTaskDesc
    *
    * @param key $keyUpdateParamDesc
    * @param task is the task to evaluate in case the `key` is missing
    * @param expiry $expParamDesc
    *
    * @return $cachedFutureReturn
    */
  def cachedTask(key: String, expiry: Duration)(task: Task[A]): Task[A]

  /** Atomic compare and set operation.
    *
    * @param key $keyUpdateParamDesc
    * @param current is the current value that is expected, or `None` in case
    *        there should be no `key` currently stored in the cache
    * @param update is the value to be persisted for the given `key`
    *        in case of success
    * @param expiry $expParamDesc
    */
  def compareAndSet(key: String, current: Option[A], update: A, expiry: Duration): Boolean

  /** Atomic transform and get operation.
    *
    * @param key $keyUpdateParamDesc
    * @param f is function to execute for updating the current value
    * @param expiry $expParamDesc
    *
    * @return the updated value
    */
  def transformAndGet(key: String, expiry: Duration)(f: Option[A] => A): A

  /** Atomic get and transform operation.
    *
    * @param key $keyUpdateParamDesc
    * @param f is function to execute for updating the current value
    * @param expiry $expParamDesc
    *
    * @return the value associated with the given `key` before the update
    */
  def getAndTransform(key: String, expiry: Duration)(f: Option[A] => A): Option[A]

  /** Returns the number of non-expired keys currently
    * stored in the cache.
    */
  def size: Int

  /** Returns the number of keys (both expired and active)
    * currently stored in the cache.
    */
  def rawSize: Int

  /** Future that completes when a maintenance window has run,
    * giving the number of items that were removed.
    */
  def nextCleanse: Task[Int]

  /** Closes this cache and performance and cleanup operations. */
  def close(): Unit
}

object TimeBasedCache {
  /** Builds an [[TimeBasedCache]] instance.
    *
    * @param cleanupPeriod is the period at which to repeat the 
    *        periodic cache cleanse
    * @param distribution is the number of atomic references to use 
    *        under the hood, useful in order to distribute the load
    * @param padding is the padding strategy to use, for performance
    *        tuning, in order to avoid "false sharing"
    * @param s is the `Scheduler` to use for scheduling the periodic 
    *        cleanse or for future-related activities
    */
  def apply[A](
    cleanupPeriod: FiniteDuration = 3.seconds,
    distribution: Int = Platform.parallelism,
    padding: PaddingStrategy = NoPadding)
    (implicit s: Scheduler): TimeBasedCache[A] = {

    require(distribution >= 1, "distribution >= 1")
    require(cleanupPeriod > Duration.Zero, "cleanupPeriod > 0")

    if (distribution == 1)
      new SingleAtomic[A](cleanupPeriod, padding)(s)
    else
      new Distributed[A](distribution, cleanupPeriod, padding)
  }

  /** Implementation that distributes the load among multiple atomic references. */
  private final class Distributed[A](distribution: Int, cleanupPeriod: FiniteDuration, ps: PaddingStrategy)
    (implicit s: Scheduler) extends TimeBasedCache[A] {

    require(distribution >= 2)

    private[this] val arraySize: Int = {
      // Rounding up to a power of two
      val lnOf2 = scala.math.log(2)
      val log2 = scala.math.log(distribution) / lnOf2
      val bit = ceil(log2)
      1 << (if (bit > 30) 30 else bit.toInt)
    }

    private[this] val modulus = arraySize - 1
    private[this] val array: Array[SingleAtomic[A]] =
      Array.fill(arraySize)(new SingleAtomic(cleanupPeriod, ps))

    private def cacheFor(key: String): SingleAtomic[A] =
      array(key.hashCode & modulus)

    override def get(key: String): Option[A] =
      cacheFor(key).get(key)
    override def getOrElse(key: String, default: A): A =
      cacheFor(key).getOrElse(key, default)
    override def add(key: String, value: A, expiry: Duration): Boolean =
      cacheFor(key).add(key, value, expiry)
    override def set(key: String, value: A, expiry: Duration): Unit =
      cacheFor(key).set(key, value, expiry)
    override def delete(key: String): Boolean =
      cacheFor(key).delete(key)
    override def cachedFuture(key: String, expiry: Duration)(cb: (Scheduler) => Future[A]): Future[A] =
      cacheFor(key).cachedFuture(key, expiry)(cb)
    override def cachedTask(key: String, expiry: Duration)(task: Task[A]): Task[A] =
      cacheFor(key).cachedTask(key, expiry)(task)
    override def compareAndSet(key: String, current: Option[A], update: A, expiry: Duration): Boolean =
      cacheFor(key).compareAndSet(key, current, update, expiry)
    override def transformAndGet(key: String, expiry: Duration)(f: (Option[A]) => A): A =
      cacheFor(key).transformAndGet(key, expiry)(f)
    override def getAndTransform(key: String, expiry: Duration)(f: (Option[A]) => A): Option[A] =
      cacheFor(key).getAndTransform(key, expiry)(f)
    override def size: Int =
      array.foldLeft(0)((acc,e) => acc + e.size)
    override def rawSize: Int =
      array.foldLeft(0)((acc,e) => acc + e.rawSize)

    override def nextCleanse: Task[Int] = {
      val tasks = array.map(_.nextCleanse).iterator
      Task.gather(tasks).map(_.sum)
    }

    override def close(): Unit =
      array.foreach(_.close())
  }

  /** Implementation that piggy-backs on top of [[TimeBasedCache]]
    * kept in an atomic reference.
    */
  private final class SingleAtomic[A](cleanupPeriod: FiniteDuration, ps: PaddingStrategy)
    (implicit scheduler: Scheduler) extends TimeBasedCache[A] {

    def get(key: String): Option[A] = {
      val now = scheduler.currentTimeMillis()
      stateRef.get.get(key, now).asInstanceOf[Option[A]]
    }

    def getOrElse(key: String, default: A): A = {
      val now = scheduler.currentTimeMillis()
      stateRef.get.getOrElse(key, default, now)
        .asInstanceOf[A]
    }

    def add(key: String, value: A, expiry: Duration): Boolean = {
      @tailrec def loop(now: Timestamp): Boolean = {
        val current = stateRef.get
        val (isSuccess, update) = current.add(key, value, expiry, now)
        if (!isSuccess) false else {
          if (stateRef.compareAndSet(current, update)) true
          else loop(now) // retry
        }
      }

      loop(scheduler.currentTimeMillis())
    }

    def set(key: String, value: A, expiry: Duration): Unit = {
      @tailrec def loop(now: Timestamp): Unit = {
        val current = stateRef.get
        val update = current.set(key, value, expiry, now)
        if (update ne current) {
          if (!stateRef.compareAndSet(current, update))
            loop(now) // retry
        }
      }

      loop(scheduler.currentTimeMillis())
    }

    @tailrec
    def delete(key: String): Boolean = {
      val current = stateRef.get
      val (isSuccess, update) = current.delete(key)
      if (!isSuccess) false else {
        if (stateRef.compareAndSet(current, update)) true
        else delete(key) // retry
      }
    }

    def cachedFuture(key: String, expiry: Duration)(f: Scheduler => Future[A]): Future[A] = {
      @tailrec def loop(now: Timestamp): Future[A] = {
        val current = stateRef.get
        current.get(key, now) match {
          case Some(future) =>
            future.asInstanceOf[Future[A]]
          case None =>
            val promise = Promise[A]()
            val update = current.set(key, promise, expiry, now)

            if (!stateRef.compareAndSet(current, update))
              loop(now) // retry
            else {
              try promise.tryCompleteWith(f(scheduler))
              catch { case NonFatal(ex) => promise.failure(ex) }
              promise.future
            }
        }
      }

      loop(scheduler.currentTimeMillis())
    }

    override def cachedTask(key: String, expiry: Duration)(task: Task[A]): Task[A] = {
      @tailrec def loop(now: Timestamp): Task[A] = {
        val current = stateRef.get

        current.get(key, now) match {
          case Some(future) =>
            future.asInstanceOf[Task[A]]
          case None =>
            val cached = task.memoize
            val update = current.set(key, cached, expiry, now)

            if (!stateRef.compareAndSet(current, update))
              loop(now) // retry
            else
              cached
        }
      }

      Task.defer(loop(scheduler.currentTimeMillis()))
    }

    def compareAndSet(key: String, expected: Option[A], update: A, expiry: Duration): Boolean = {
      @tailrec def loop(now: Timestamp): Boolean = {
        val current = stateRef.get
        val (isSuccess, cacheUpdate) =
          current.compareAndSet(key, expected, update, expiry, now)

        if (!isSuccess) false else {
          if (stateRef.compareAndSet(current, cacheUpdate)) true
          else loop(now) // retry
        }
      }

      loop(scheduler.currentTimeMillis())
    }

    def transformAndGet(key: String, expiry: Duration)(cb: (Option[A]) => A): A = {
      @tailrec def loop(now: Timestamp): A = {
        val current = stateRef.get
        val (value, update) =
          current.transformAndGet(key, expiry, now)(cb.asInstanceOf[Option[Any] => A])

        if (stateRef.compareAndSet(current, update)) value.asInstanceOf[A]
        else loop(now) // retry
      }

      loop(scheduler.currentTimeMillis())
    }

    def getAndTransform(key: String, expiry: Duration)(cb: (Option[A]) => A): Option[A] = {
      @tailrec def loop(now: Timestamp): Option[A] = {
        val current = stateRef.get
        val (value, update) =
          current.getAndTransform(key, expiry, now)(cb.asInstanceOf[Option[Any] => A])

        if (stateRef.compareAndSet(current, update)) value.asInstanceOf[Option[A]]
        else loop(now) // retry
      }

      loop(scheduler.currentTimeMillis())
    }

    def cleanse(): Int = {
      val difference = stateRef.transformAndExtract { current =>
        val now = scheduler.currentTimeMillis()
        current.cleanse(now)
      }

      val old = maintenancePromise.getAndSet(Promise())
      old.success(difference)
      difference
    }

    def size: Int = {
      val ts = scheduler.currentTimeMillis()
      stateRef.get.size(ts)
    }

    def rawSize: Int = 
      stateRef.get.rawSize

    def nextCleanse: Task[Int] =
      Task.deferFuture(maintenancePromise.get.future)

    def close(): Unit = {
      task.cancel()
      stateRef.set(ImmutableTimeBasedCache.empty)
    }

    private[this] val task =
      scheduler.scheduleWithFixedDelay(cleanupPeriod, cleanupPeriod) {
        cleanse()
      }

    private[this] val maintenancePromise =
      AtomicAny(Promise[Int]())
    private[this] val stateRef: AtomicAny[ImmutableTimeBasedCache[Any]] =
      AtomicAny.withPadding(ImmutableTimeBasedCache.empty, ps)
  }
}
