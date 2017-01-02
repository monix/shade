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
        CancelableFuture.successful(cache.add(key, codec.serialize(value).toSeq, exp))
    }

  def set[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): CancelableFuture[Unit] =
    value match {
      case null =>
        CancelableFuture.successful(())
      case _ =>
        CancelableFuture.successful(cache.set(key, codec.serialize(value).toSeq, exp))
    }

  def delete(key: String): CancelableFuture[Boolean] =
    CancelableFuture.successful(cache.delete(key))

  def get[T](key: String)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.get[Seq[Byte]](key)).map(_.map(x => codec.deserialize(x.toArray)))

  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    Future.successful(cache.compareAndSet(key, expecting.map(x => codec.serialize(x).toSeq), codec.serialize(newValue).toSeq, exp))

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[T] =
    Future.successful(cache.transformAndGet[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    }) map { update =>
      codec.deserialize(update.toArray)
    }

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.getAndTransform[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    }) map { update =>
      update.map(x => codec.deserialize(x.toArray))
    }

  def increment(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long] = {
    def toBigInt(bytes: Seq[Byte]): BigInt = BigInt(new String(bytes.toArray))
    Future.successful(cache.transformAndGet[Seq[Byte]](key, exp) {
      case Some(current) => (toBigInt(current) + by).toString.getBytes
      case None if default.isDefined => default.get.toString.getBytes
      case None => throw new UnhandledStatusException(s"For key $key - CASNotFoundStatus")
    }).map(toBigInt).map(_.toLong)
  }

  def decrement(key: String, by: Long, default: Option[Long], exp: Duration): Future[Long] = {
    def toBigInt(bytes: Seq[Byte]): BigInt = BigInt(new String(bytes.toArray))
    Future.successful(cache.transformAndGet[Seq[Byte]](key, exp) {
      case Some(current) => (toBigInt(current) - by).max(0).toString.getBytes
      case None if default.isDefined => default.get.toString.getBytes
      case None => throw new UnhandledStatusException(s"For key $key - CASNotFoundStatus")
    }).map(toBigInt).map(_.toLong)
  }

  def close(): Unit = {
    cache.close()
  }

  private[this] val cache = InMemoryCache(context)
}
