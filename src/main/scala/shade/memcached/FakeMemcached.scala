package shade.memcached

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import shade.inmemory.InMemoryCache


class FakeMemcached(implicit ec: ExecutionContext) extends Memcached {
  def add[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    value match {
      case null =>
        Future.successful(false)
      case _ =>
        cache.add(key, codec.serialize(value).toSeq, exp)
    }

  def set[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Unit] =
    value match {
      case null =>
        Future.successful(())
      case _ =>
        cache.set(key, codec.serialize(value).toSeq, exp)
    }

  def delete(key: String): Future[Boolean] =
    cache.delete(key)

  def get[T](key: String)(implicit codec: Codec[T]): Future[Option[T]] =
    cache.get[Seq[Byte]](key).map(_.map(x => codec.deserialize(x.toArray)))

  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    cache.compareAndSet(key, expecting.map(x => codec.serialize(x).toSeq), codec.serialize(newValue).toSeq, exp)

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[T] =
    cache.transformAndGet[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    } map { update =>
      codec.deserialize(update.toArray)
    }

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[Option[T]] =
    cache.getAndTransform[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    } map { update =>
      update.map(x => codec.deserialize(x.toArray))
    }

  def shutdown() {
    cache.shutdown()
  }

  private[this] val cache = InMemoryCache(1000)
}
