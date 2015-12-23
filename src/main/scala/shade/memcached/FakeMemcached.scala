package shade.memcached

import shade.inmemory.InMemoryCache

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

class FakeMemcached(context: ExecutionContext) extends Memcached {
  private[this] implicit val ec = context

  override def add[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    value match {
      case null =>
        Future.successful(false)
      case _ =>
        Future.successful(cache.add(key, codec.serialize(value).toSeq, exp))
    }

  override def set[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Unit] =
    value match {
      case null =>
        Future.successful(())
      case _ =>
        Future.successful(cache.set(key, codec.serialize(value).toSeq, exp))
    }

  override def delete(key: String): Future[Boolean] =
    Future.successful(cache.delete(key))

  override def get[T](key: String)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.get[Seq[Byte]](key)).map(_.map(x => codec.deserialize(x.toArray)))

  override def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    Future.successful(cache.compareAndSet(key, expecting.map(x => codec.serialize(x).toSeq), codec.serialize(newValue).toSeq, exp))

  override def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[T] =
    Future.successful(cache.transformAndGet[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    }) map { update =>
      codec.deserialize(update.toArray)
    }

  override def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(cache.getAndTransform[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    }) map { update =>
      update.map(x => codec.deserialize(x.toArray))
    }

  override def close(): Unit = {
    cache.close()
  }

  private[this] val cache = InMemoryCache(context)
}
