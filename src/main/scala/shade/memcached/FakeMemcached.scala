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

import monix.execution.CancelableFuture
import net.spy.memcached.CachedData
import shade.UnhandledStatusException
import shade.inmemory.InMemoryCache

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

class FakeMemcached(context: ExecutionContext) extends Memcached {
  private[this] implicit val ec = context

  def add[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): CancelableFuture[Boolean] =
    value match {
      case null =>
        CancelableFuture.successful(false)
      case _ =>
        CancelableFuture.successful(cache.add[CachedData](key, codec.encode(value), exp))
    }

  def set[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): CancelableFuture[Unit] =
    value match {
      case null =>
        CancelableFuture.successful(())
      case _ =>
        CancelableFuture.successful(cache.set[CachedData](key, codec.encode(value), exp))
    }

  def delete(key: String): CancelableFuture[Boolean] =
    CancelableFuture.successful(cache.delete(key))

  def get[T](key: String)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.get[CachedData](key).map(codec.decode))

  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    Future.successful {
      val current = cache.get[CachedData](key)
      if (current.map(codec.decode) == expecting) {
        cache.set(key, codec.encode(newValue), exp)
        true
      } else {
        false
      }
    }

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[T] =
    Future.successful(cache.transformAndGet[CachedData](key, exp)(o => codec.encode(cb(o.map(codec.decode))))).map(codec.decode)

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.getAndTransform[CachedData](key: String, exp)(o => codec.encode(cb(o.map(codec.decode))))).map(c => c.map(codec.decode))

  def increment(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long] = {
    def toBigInt(bytes: Array[Byte]): BigInt = BigInt(new String(bytes))
    Future.successful(cache.transformAndGet[CachedData](key, exp) {
      case Some(current) => new CachedData(0, (toBigInt(current.getData) + by).toString.getBytes, Int.MaxValue)
      case None if default.isDefined => new CachedData(0, default.get.toString.getBytes, Int.MaxValue)
      case None => throw new UnhandledStatusException(s"For key $key - CASNotFoundStatus")
    }).map(c => toBigInt(c.getData)).map(_.toLong)
  }

  def decrement(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long] = {
    def toBigInt(bytes: Array[Byte]): BigInt = BigInt(new String(bytes))
    Future.successful(cache.transformAndGet[CachedData](key, exp) {
      case Some(current) => new CachedData(0, (toBigInt(current.getData) - by).max(0).toString.getBytes, Int.MaxValue)
      case None if default.isDefined => new CachedData(0, default.get.toString.getBytes, Int.MaxValue)
      case None => throw new UnhandledStatusException(s"For key $key - CASNotFoundStatus")
    }).map(c => toBigInt(c.getData)).map(_.toLong)
  }

  def close(): Unit = {
    cache.close()
  }

  private[this] val cache = InMemoryCache(context)
}
