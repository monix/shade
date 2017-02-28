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
import shade.local.mutable.TimeBasedCache

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** A fake [[Memcached]] implementation that can be used in testing. */
class FakeMemcached(scheduler: Scheduler) extends Memcached {
  private[this] val inMemory = TimeBasedCache[CachedData]()(scheduler)

  def addL[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T]): Task[Boolean] =
    Task.eval(inMemory.add(key, codec.encode(value), exp))

  def add[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean] = {

    val r = inMemory.add(key, codec.encode(value), exp)
    CancelableFuture.successful(r)
  }

  def setL[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T]): Task[Unit] =
    Task.eval(inMemory.set(key, codec.encode(value), exp))

  def set[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Unit] =
    CancelableFuture.successful {
      inMemory.set(key, codec.encode(value), exp)
    }

  def deleteL(key: String): Task[Boolean] =
    Task.eval(inMemory.delete(key))

  def delete(key: String)(implicit ec: ExecutionContext): CancelableFuture[Boolean] =
    CancelableFuture.successful(inMemory.delete(key))

  def getL[T](key: String)(implicit codec: Codec[T]): Task[Option[T]] =
    Task.eval(inMemory.get(key).map(codec.decode))

  def get[T](key: String)(implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[T]] =
    CancelableFuture.successful(inMemory.get(key).map(codec.decode))

  def getsL[T](key: String)(implicit codec: Codec[T]): Task[Option[CASValue[T]]] =
    Task.eval(
      inMemory.get(key).map { data =>
        val v = codec.decode(data)
        new CASValue(v.hashCode(), v)
      })

  def gets[T](key: String)(implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[CASValue[T]]] =
    CancelableFuture.successful(
      inMemory.get(key).map { data =>
        val v = codec.decode(data)
        new CASValue(v.hashCode(), v)
      })

  private def cas[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T]): Boolean = {

    inMemory.get(key) match {
      case None => false
      case current @ Some(data) =>
        val v = codec.decode(data)
        if (casId != v.hashCode()) false else {
          val u = codec.encode(update)
          inMemory.compareAndSet(key, current, u, exp)
        }
    }
  }

  def rawCompareAndSetL[T](key: String, casId: Long, update: T, exp: Duration)(implicit codec: Codec[T]): Task[Boolean] =
    Task.eval(cas(key, casId, update, exp)(codec))

  def rawCompareAndSet[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean] =
    CancelableFuture.successful(cas(key, casId, update, exp)(codec))

  private def incAndGet(key: String, by: Long, default: Long, exp: Duration): Long = {
    val codec = implicitly[Codec[Long]]
    val ref = inMemory.transformAndGet(key, exp) {
      case None => codec.encode(default)
      case Some(data) =>
        val current = codec.decode(data)
        codec.encode(current + by)
    }
    codec.decode(ref)
  }

  def incrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long] =
    Task.eval(incAndGet(key, by, default, exp))

  def incrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long] =
    CancelableFuture.successful(incAndGet(key, by, default, exp))

  def decrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long] =
    Task.eval(incAndGet(key, -1 * by, default, exp))

  def decrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long] =
    CancelableFuture.successful(incAndGet(key, -1 * by, default, exp))

  def close(): Unit =
    inMemory.close()
}
