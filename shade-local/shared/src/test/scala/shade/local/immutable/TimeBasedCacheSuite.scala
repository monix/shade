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

package shade.local.immutable

import minitest.SimpleTestSuite
import scala.concurrent.duration._

object TimeBasedCacheSuite extends SimpleTestSuite {
  test("simple set and get") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", 1.minute, now)
      .set("key2", "value2", 1.minute, now)

    assertEquals(cache.get("key1", now), Some("value1"))
    assertEquals(cache.get("key1", now + 59.seconds.toMillis), Some("value1"))
    assertEquals(cache.get("key1", now + 60.seconds.toMillis), None)

    assertEquals(cache.get("key2", now), Some("value2"))
    assertEquals(cache.get("key2", now + 59.seconds.toMillis), Some("value2"))
    assertEquals(cache.get("key2", now + 60.seconds.toMillis), None)

    assertEquals(cache.keysToValues.size, 2)
    assertEquals(cache.expiryOrder.size, 1)

    val (diff1, cleansed1) = cache.cleanse(now + 59.seconds.toMillis)
    assertEquals(diff1, 0)
    assertEquals(cleansed1, cache)

    val (diff2, cleansed2) = cache.cleanse(now + 60.seconds.toMillis)
    assertEquals(diff2, 2)
    assertEquals(cleansed2.keysToValues.size, 0)
    assertEquals(cleansed2.expiryOrder.size, 0)
  }

  test("set on a duplicate") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", 1.minute, now - 10.seconds.toMillis)
      .set("key2", "value2", 1.minute, now - 10.seconds.toMillis)
      .set("key1", "value1-updated", 1.minute, now)

    assertEquals(cache.get("key1", now), Some("value1-updated"))
    assertEquals(cache.expiryOrder.size, 2)
    assert(cache.expiryOrder(now + 50.seconds.toMillis).contains("key2"))
    assert(cache.expiryOrder(now + 60.seconds.toMillis).contains("key1"))

    val (diff1, cleansed1) = cache.cleanse(now + 50.seconds.toMillis)
    assertEquals(diff1, 1)
    assertEquals(cleansed1.get("key1", now), Some("value1-updated"))

    val (diff2, cleansed2) = cache.cleanse(now + 60.seconds.toMillis)
    assertEquals(diff2, 2)
    assertEquals(cleansed2.get("key1", now), None)

    val (diff3, cleansed3) = cleansed1.cleanse(now + 60.seconds.toMillis)
    assertEquals(diff3, 1)
    assertEquals(cleansed3.get("key1", now), None)
  }
  
  test("delete") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", 1.minute, now - 10.seconds.toMillis)
      .set("key2", "value2", 1.minute, now - 10.seconds.toMillis)
      .set("key3", "value3", 1.minute, now)
    
    val (isSuccess1, updated1) = cache.delete("key1")
    assert(isSuccess1, "isSuccess1")
    assertEquals(updated1.get("key1", now), None)
    assertEquals(updated1.get("key2", now), Some("value2"))
    assertEquals(updated1.get("key3", now), Some("value3"))

    val (isSuccess2, updated2) = cache.delete("key2")
    assert(isSuccess2, "isSuccess2")
    assertEquals(updated2.get("key1", now), Some("value1"))
    assertEquals(updated2.get("key2", now), None)
    assertEquals(updated2.get("key3", now), Some("value3"))

    val (isSuccess3, updated3) = cache.delete("key3")
    assert(isSuccess3, "isSuccess3")
    assertEquals(updated3.get("key1", now), Some("value1"))
    assertEquals(updated3.get("key2", now), Some("value2"))
    assertEquals(updated3.get("key3", now), None)

    val (isSuccess4, updated4) = cache.delete("key4")
    assert(!isSuccess4, "!isSuccess4")
    assertEquals(updated4, cache)
  }

  test("add") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", 1.minute, now - 10.seconds.toMillis)
      .set("key2", "value2", 1.minute, now - 10.seconds.toMillis)
      .set("key3", "value3", 1.minute, now)

    val (isSuccess1, updated1) = cache.add("key4", "value4", 1.minute, now)
    assert(isSuccess1, "isSuccess1")
    assertEquals(updated1.get("key1", now), Some("value1"))
    assertEquals(updated1.get("key2", now), Some("value2"))
    assertEquals(updated1.get("key3", now), Some("value3"))
    assertEquals(updated1.get("key4", now), Some("value4"))

    val (isSuccess2, updated2) = cache.add("key1", "value1", 1.minute, now)
    assert(!isSuccess2, "!isSuccess2")
    assertEquals(updated2, cache)

    val (isSuccess3, updated3) = cache.add("key1", "value1-updated", 1.minute, now + 1.minute.toMillis)
    assert(isSuccess3, "isSuccess3")
    assertEquals(updated3.get("key1", now), Some("value1-updated"))
    assertEquals(updated3.get("key2", now), Some("value2"))
    assertEquals(updated3.get("key3", now), Some("value3"))
  }

  test("compareAndSet") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", 1.minute, now - 10.seconds.toMillis)
      .set("key2", "value2", 1.minute, now)

    val (isSuccess1, updated1) = cache.compareAndSet("key3", None, "value3", 1.minute, now)
    assert(isSuccess1, "isSuccess1")
    assertEquals(updated1.get("key1", now), Some("value1"))
    assertEquals(updated1.get("key2", now), Some("value2"))
    assertEquals(updated1.get("key3", now), Some("value3"))

    val (isSuccess2, updated2) = cache.compareAndSet("key3", Some("valueX"), "value3", 1.minute, now)
    assert(!isSuccess2, "!isSuccess2")
    assertEquals(updated2, cache)

    val (isSuccess3, updated3) = cache.compareAndSet("key1", None, "value1-updated", 1.minute, now + 1.minute.toMillis)
    assert(isSuccess3, "isSuccess3")
    assertEquals(updated3.get("key1", now), Some("value1-updated"))
    assertEquals(updated3.get("key2", now), Some("value2"))
  }
  
  test("transformAndGet") {
    val now = System.currentTimeMillis()
    var cache = TimeBasedCache.empty[Int]

    for (i <- 0 until 10) {
      val (value, update) = cache.transformAndGet("key1", 1.minute, now) { case None => 0; case Some(x) => x + 1 }
      assertEquals(update.get("key1", now), Some(i))
      assertEquals(value, i)
      cache = update
    }
  }

  test("getAndTransform") {
    val now = System.currentTimeMillis()
    var cache = TimeBasedCache.empty[Int]

    for (i <- 0 until 10) {
      val expected = if (i == 0) None else Some(i-1)
      val (value, update) = cache.getAndTransform("key1", 1.minute, now) { case None => 0; case Some(x) => x + 1 }
      assertEquals(update.get("key1", now), Some(i))
      assertEquals(value, expected)
      cache = update
    }
  }

  test("transformAndExtract") {
    val now = System.currentTimeMillis()
    var cache = TimeBasedCache.empty[Int]

    for (i <- 0 until 10) {
      val (value, update) = cache.transformAndExtract("key1", 1.minute, now) {
        case None => (1.toString, 0)
        case Some(x) => ((x + 2).toString, x + 1)
      }

      assertEquals(update.get("key1", now), Some(i))
      assertEquals(value, (i+1).toString)
      cache = update
    }
  }

  test("infinite expiry should not add to expiryOrder") {
    val now = System.currentTimeMillis()
    val cache = TimeBasedCache.empty[String]
      .set("key1", "value1", Duration.Inf, now)
      .set("key2", "value2", Duration.Inf, now)

    assert(cache.expiryOrder.isEmpty, "expiryOrder.isEmpty")

    val (diff, cleansed) = cache.cleanse(now + 366.days.toMillis)
    assertEquals(diff, 0)
    assertEquals(cleansed, cache)

    val (isSuccess, update) = cache.delete("key1")
    assert(isSuccess, "isSuccess")
    assertEquals(update.get("key1", now), None)
    assertEquals(update.get("key2", now), Some("value2"))
  }
}
