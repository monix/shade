package shade.memcached

import shade.{InMemoryCache, Memcached}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}


class FakeMemcached(implicit ec: ExecutionContext) extends Memcached {
  import shade.inmemory.defaultCodecs._
  
  def add[T](key: String, value: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Boolean] =
    if (value != null)
      cache.add(key, codec.serialize(value).toSeq, exp)
    else
      Future.successful(false)

  def set[T](key: String, value: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Unit] =
    if (value != null)
      cache.set(key, codec.serialize(value).toSeq, exp)
    else
      Future.successful(())

  def delete(key: String): Future[Boolean] =
    cache.delete(key)

  def get[T](key: String)(implicit codec: Memcached#Codec[T]): Future[Option[T]] =
    cache.get[Seq[Byte]](key).map(_.map(x => codec.deserialize(x.toArray)))

  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Boolean] =
    cache.compareAndSet(key, expecting.map(x => codec.serialize(x).toSeq), codec.serialize(newValue).toSeq, exp)

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Memcached#Codec[T]): Future[T] =
    cache.transformAndGet[Seq[Byte]](key: String, exp) { current =>
      val cValue = current.map(x => codec.deserialize(x.toArray))
      val update = cb(cValue)
      codec.serialize(update).toSeq
    } map { update =>
      codec.deserialize(update.toArray)
    }

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Memcached#Codec[T]): Future[Option[T]] =
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
