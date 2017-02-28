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

package shade.local.mutable

import minitest.TestSuite
import monix.eval.Task
import monix.execution.schedulers.TestScheduler

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

object TimeBasedCacheSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit =
    assert(env.state.tasks.isEmpty, "tasks.isEmpty")

  test("simple set and get") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4)
    try {
      cache.set("key1", "value1", 1.minute + 10.seconds)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute)

      assertEquals(cache.get("key1"), Some("value1"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), Some("value3"))

      s.tick(59.seconds)
      assertEquals(cache.get("key1"), Some("value1"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), Some("value3"))

      s.tick(1.second)
      assertEquals(cache.get("key1"), Some("value1"))
      assertEquals(cache.get("key2"), None)
      assertEquals(cache.get("key3"), None)

      s.tick(10.seconds)
      assertEquals(cache.get("key1"), None)
    }
    finally {
      cache.close()
    }
  }

  test("set on a duplicate") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4)
    try {
      cache.set("key1", "value1", 1.minute)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute)

      cache.set("key1", "value1-updated", 1.minute + 10.seconds)

      assertEquals(cache.get("key1"), Some("value1-updated"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), Some("value3"))

      s.tick(59.seconds)
      assertEquals(cache.get("key1"), Some("value1-updated"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), Some("value3"))

      s.tick(1.second)
      assertEquals(cache.get("key1"), Some("value1-updated"))
      assertEquals(cache.get("key2"), None)
      assertEquals(cache.get("key3"), None)

      s.tick(10.seconds)
      assertEquals(cache.get("key1"), None)
    }
    finally {
      cache.close()
    }
  }

  test("delete") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4)
    try {
      cache.set("key1", "value1", 1.minute)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute + 10.seconds)

      assertEquals(cache.get("key1"), Some("value1"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), Some("value3"))

      cache.delete("key3")

      assertEquals(cache.get("key1"), Some("value1"))
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), None)

      cache.delete("key1")

      assertEquals(cache.get("key1"), None)
      assertEquals(cache.get("key2"), Some("value2"))
      assertEquals(cache.get("key3"), None)

      s.tick(60.seconds)
      assertEquals(cache.get("key2"), None)
    }
    finally {
      cache.close()
    }
  }

  test("add") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4)
    try {
      cache.set("key1", "value1", 1.minute)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute + 10.seconds)

      assert(cache.add("key4", "value4", 1.minute), "cache.add")
      assertEquals(cache.get("key4"), Some("value4"))

      assert(!cache.add("key1", "update", 1.minute), "!cache.add")
      assert(!cache.add("key4", "update", 1.minute), "!cache.add")
    }
    finally {
      cache.close()
    }
  }

  test("compareAndSet") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4)
    try {
      cache.set("key1", "value1", 1.minute)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute + 10.seconds)

      assert(cache.compareAndSet("key4", None, "value4", 1.minute))
      assert(!cache.compareAndSet("key5", Some("missing"), "value5", 1.minute))
      assert(cache.compareAndSet("key1", Some("value1"), "value1-updated", 1.minute))
      assert(!cache.compareAndSet("key2", Some("wrong"), "value2-updated", 1.minute))

      assertEquals(cache.get("key4"), Some("value4"))
      assertEquals(cache.get("key5"), None)
      assertEquals(cache.get("key1"), Some("value1-updated"))
      assertEquals(cache.get("key2"), Some("value2"))
    }
    finally {
      cache.close()
    }
  }

  test("transformAndGet") { implicit s =>
    val cache = TimeBasedCache[Int](distribution = 4)
    try {
      for (i <- 0 until 10) {
        val value = cache.transformAndGet("test", 1.minute) {
          case None => 0
          case Some(x) => x + 1
        }

        assertEquals(value, i)
      }
    }
    finally {
      cache.close()
    }
  }

  test("getAndTransform") { implicit s =>
    val cache = TimeBasedCache[Int](distribution = 4)
    try {
      for (i <- 0 until 10) {
        val value = cache.getAndTransform("test", 1.minute) {
          case None => 0
          case Some(x) => x + 1
        }

        assertEquals(value, if (i == 0) None else Some(i-1))
      }
    }
    finally {
      cache.close()
    }
  }

  test("nextCleanse") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4, cleanupPeriod = 1.second)
    try {
      cache.set("key1", "value1", 1.minute + 10.seconds)
      cache.set("key2", "value2", 1.minute)
      cache.set("key3", "value3", 1.minute)

      assertEquals(cache.size, 3)
      assertEquals(cache.rawSize, 3)

      s.tick(1.minute - 1.second)
      val next1 = cache.nextCleanse.runAsync
      s.tick(); assertEquals(next1.value, None)

      s.tick(1.second)
      assertEquals(next1.value, Some(Success(2)))
      assertEquals(cache.size, 1)
      assertEquals(cache.rawSize, 1)

      s.tick(9.seconds)
      val next2 = cache.nextCleanse.runAsync
      s.tick(); assertEquals(next2.value, None)

      s.tick(1.second)
      assertEquals(next2.value, Some(Success(1)))

      val next3 = cache.nextCleanse.runAsync
      s.tick(); assertEquals(next3.value, None)

      s.tick(1.second)
      assertEquals(next3.value, Some(Success(0)))
    }
    finally {
      cache.close()
    }
  }

  test("cache future") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4, cleanupPeriod = 1.second)
    try {
      var effect = 0
      def fetch(): Future[String] =
        cache.cachedFuture("hello", 1.minute) { implicit s =>
          Future {
            effect += 1
            s"world$effect"
          }
        }

      val f1 = fetch(); s.tick()
      assertEquals(f1.value, Some(Success("world1")))
      val f2 = fetch(); s.tick()
      assertEquals(f2.value, Some(Success("world1")))

      s.tick(1.minute)
      val f3 = fetch(); s.tick()
      assertEquals(f3.value, Some(Success("world2")))
    }
    finally {
      cache.close()
    }
  }

  test("cache task") { implicit s =>
    val cache = TimeBasedCache[String](distribution = 4, cleanupPeriod = 1.second)
    try {
      var effect = 0
      val task = cache.cachedTask("hello", 1.minute) {
        Task {
          effect += 1
          s"world$effect"
        }
      }

      val f1 = task.runAsync; s.tick()
      assertEquals(f1.value, Some(Success("world1")))
      val f2 = task.runAsync; s.tick()
      assertEquals(f2.value, Some(Success("world1")))

      s.tick(1.minute)
      val f3 = task.runAsync; s.tick()
      assertEquals(f3.value, Some(Success("world2")))
    }
    finally {
      cache.close()
    }
  }
}
