package shade.inmemory

import concurrent.duration.Duration
import concurrent.Future
import shade.concurrency.atomic.Ref
import annotation.tailrec
import shade.{CacheException, InMemoryCache}


/**
 * Simple and dumb implementation of an in-memory cache.
 */
class InMemoryCacheImpl(maxElems: Int = 100) extends InMemoryCache {
  override def awaitGet[T](key: String)(implicit codec: Codec[T]): Option[T] =
    cacheRef.get.get(key).flatMap {
      case value if !isExpired(value) =>
        Some(codec.deserialize(value.value))
      case _ =>
        None
    }

  def asyncGet[T](key: String)(implicit codec: Codec[T]): Future[Option[T]] =
    Future.successful(awaitGet(key))


  override def awaitAdd[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Boolean =
    if (value == null)
      false

    else
      cacheRef.transformAndExtract { map =>
        val existingValue = map.get(key).filterNot(v => isExpired(v))

        existingValue match {
          case Some(_) =>
            (map, false)
          case None =>
            val newMap = makeRoom(map).updated(key, CacheValue(codec.serialize(value), exp))
            (newMap, true)
        }
      }

  def asyncAdd[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    Future.successful(awaitAdd(key, value, exp))

  override def awaitSet[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]) {
    if (value != null)
      cacheRef.transform { map =>
        val existingValue = map.get(key).filterNot(v => isExpired(v))

        existingValue match {
          case Some(v) =>
            map.updated(key, CacheValue(codec.serialize(value), exp))
          case None =>
            val limitedMap = makeRoom(map)
            val newMap = limitedMap.updated(key, CacheValue(codec.serialize(value), exp))
            newMap
        }
      }
  }

  def asyncSet[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Future[Unit] = {
    awaitSet(key, value, exp)
    Future.successful(())
  }


  override def awaitDelete(key: String): Boolean = {
    val oldMap = cacheRef.getAndTransform { map =>
      map - key
    }
    oldMap.contains(key) && !isExpired(oldMap(key))
  }

  def asyncDelete(key: String): Future[Boolean] = {
    Future.successful(awaitDelete(key))
  }

  private[this] def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Boolean = {
    val currentTS = System.currentTimeMillis()
    val expectingS = expecting.map(codec.serialize)

    cacheRef.transformAndExtract { map =>
      map.get(key) match {
        case current @ Some(v) if !isExpired(v, currentTS) =>
          if (current.map(_.value) == expectingS)  {
            (map.updated(key, CacheValue(codec.serialize(newValue), exp)), true)
          }
          else
            (map, false)
        case _ =>
          if (expecting == None) {
            val limited = makeRoom(map)
            (limited.updated(key, CacheValue(codec.serialize(newValue), exp)), true)
          }
          else
            (map, false)
      }
    }
  }

  def asyncCAS[T](key: String, expecting: Option[T], newValue: T, exp: Duration)(implicit codec: Codec[T]): Future[Boolean] =
    Future.successful(compareAndSet(key, expecting, newValue, exp))

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[T] = {
    @tailrec
    def loop: T = {
      val current = cacheRef.get.get(key).collect { case v if !isExpired(v) => codec.deserialize(v.value) }
      val update = cb(current)

      if (!compareAndSet(key, current, update, exp))
        loop
      else
        update
    }

    Future.successful(loop)
  }

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T)(implicit codec: Codec[T]): Future[Option[T]] = {
    @tailrec
    def loop: Option[T] = {
      val current = cacheRef.get.get(key).collect { case v if !isExpired(v) => codec.deserialize(v.value) }
      val update = cb(current)

      if (!compareAndSet(key, current, update, exp))
        loop
      else
        current
    }

    Future.successful(loop)
  }

  def shutdown() {
    cacheRef.set(Map.empty)
  }

  private[this] def makeRoom(map: Map[String, CacheValue]): Map[String, CacheValue] = {
    val currentTS = System.currentTimeMillis()
    val newMap = if (map.size >= maxElems)
      map.filterNot { case (key, value) => isExpired(value, currentTS) }
    else
      map

    if (newMap.size < maxElems)
      newMap
    else
      throw new CacheException("InMemoryCache cannot hold any more elements")
  }

  private[this] def isExpired(value: CacheValue, currentTS: Long = System.currentTimeMillis()) =
    value.expiresTS <= currentTS

  private[this] val cacheRef = Ref(Map.empty[String, CacheValue])
}
