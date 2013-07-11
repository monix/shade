package shade.memcached

import shade.{InMemoryCache, Memcached}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}


class FakeMemcached(implicit ec: ExecutionContext) extends Memcached {
  import shade.inmemory.defaultCodecs._
  
  def asyncAdd[T](key: String, value: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Boolean] = 
    if (value != null)
      cache.asyncAdd(key, codec.serialize(value).toSeq, exp)
    else
      Future.successful(false)

  def asyncSet[T](key: String, value: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Unit] =
    if (value != null)
      cache.asyncSet(key, codec.serialize(value).toSeq, exp)
    else
      Future.successful(())

  def asyncDelete(key: String): Future[Boolean] =
    cache.asyncDelete(key)

  def asyncGet[T](key: String)(implicit codec: Memcached#Codec[T]): Future[Option[T]] =
    cache.asyncGet[Seq[Byte]](key).map(_.map(x => codec.deserialize(x.toArray)))

  def asyncCAS[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Memcached#Codec[T]): Future[Boolean] =
    cache.asyncCAS(key, expecting.map(x => codec.serialize(x).toSeq), codec.serialize(newValue).toSeq, exp)

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
