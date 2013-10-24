package shade.inmemory

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.annotation.tailrec
import shade.CacheException
import scala.concurrent.atomic.Atomic

class InMemoryCacheImpl(maxElems: Int) extends InMemoryCache {
  override def awaitGet[T](key: String): Option[T] =
    cacheRef.get.get(key).flatMap {
      case value if !isExpired(value) =>
        Some(value.value.asInstanceOf[T])
      case _ =>
        None
    }

  def get[T](key: String): Future[Option[T]] =
    Future.successful(awaitGet(key))

  override def awaitAdd[T](key: String, value: T, exp: Duration): Boolean =
    value match {
      case null =>
        false
      case _ =>
        cacheRef.transformAndExtract { map =>
          val existingValue = map.get(key).filterNot(v => isExpired(v))

          existingValue match {
            case Some(_) =>
              (map, false)
            case None =>
              val newMap = makeRoom(map).updated(key, CacheValue(value, exp))
              (newMap, true)
          }
        }
    }

  def add[T](key: String, value: T, exp: Duration): Future[Boolean] =
    Future.successful(awaitAdd(key, value, exp))

  override def awaitSet[T](key: String, value: T, exp: Duration) =
    value match {
      case null =>
        cacheRef.transform { map =>
          val existingValue = map.get(key).filterNot(v => isExpired(v))

          existingValue match {
            case Some(v) =>
              map.updated(key, CacheValue(value, exp))
            case None =>
              val limitedMap = makeRoom(map)
              val newMap = limitedMap.updated(key, CacheValue(value, exp))
              newMap
          }
        }

      case _ =>
        cacheRef.transform { map =>
          val existingValue = map.get(key).filterNot(v => isExpired(v))

          existingValue match {
            case Some(v) =>
              map.updated(key, CacheValue(value, exp))
            case None =>
              val limitedMap = makeRoom(map)
              val newMap = limitedMap.updated(key, CacheValue(value, exp))
              newMap
          }
        }
    }

  def set[T](key: String, value: T, exp: Duration): Future[Unit] = {
    awaitSet(key, value, exp)
    Future.successful(())
  }


  override def awaitDelete(key: String): Boolean = {
    val oldMap = cacheRef.getAndTransform { map =>
      map - key
    }
    oldMap.contains(key) && !isExpired(oldMap(key))
  }

  def delete(key: String): Future[Boolean] = {
    Future.successful(awaitDelete(key))
  }

  private[this] def cas[T](key: String, expecting: Option[T], newValue: T, exp: Duration): Boolean = {
    val currentTS = System.currentTimeMillis()

    cacheRef.transformAndExtract { map =>
      map.get(key) match {
        case current @ Some(v) if !isExpired(v, currentTS) =>
          if (current.map(_.value) == expecting)  {
            (map.updated(key, CacheValue(newValue, exp)), true)
          }
          else
            (map, false)
        case _ =>
          if (expecting == None) {
            val limited = makeRoom(map)
            (limited.updated(key, CacheValue(newValue, exp)), true)
          }
          else
            (map, false)
      }
    }
  }

  def compareAndSet[T](key: String, expecting: Option[T], newValue: T, exp: Duration): Future[Boolean] =
    Future.successful(cas(key, expecting, newValue, exp))

  def transformAndGet[T](key: String, exp: Duration)(cb: (Option[T]) => T): Future[T] = {
    @tailrec
    def loop: T = {
      val current = cacheRef.get.get(key).collect { case v if !isExpired(v) => v.value.asInstanceOf[T] }
      val update = cb(current)

      if (!cas(key, current, update, exp))
        loop
      else
        update
    }

    Future.successful(loop)
  }

  def getAndTransform[T](key: String, exp: Duration)(cb: (Option[T]) => T): Future[Option[T]] = {
    @tailrec
    def loop: Option[T] = {
      val current = cacheRef.get.get(key).collect { case v if !isExpired(v) => v.value.asInstanceOf[T] }
      val update = cb(current)

      if (!cas(key, current, update, exp))
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

  private[this] val cacheRef = Atomic(Map.empty[String, CacheValue])
}
