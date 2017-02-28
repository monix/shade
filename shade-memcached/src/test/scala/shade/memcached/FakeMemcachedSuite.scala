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

import java.io.{ByteArrayOutputStream, ObjectOutputStream}

import minitest.SimpleTestSuite
import monix.execution.CancelableFuture
import monix.execution.Scheduler.Implicits.global
import shade.memcached.testModels.{Impression, Value}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object FakeMemcachedSuite extends SimpleTestSuite with MemcachedTestHelpers {
  implicit val timeout = 5.second

  test("add") {
    withFakeMemcached { cache =>
      val op1 = cache.awaitAdd("hello", Value("world"), 5.seconds)
      assertEquals(op1, true)

      val stored = cache.awaitGet[Value]("hello")
      assertEquals(stored, Some(Value("world")))

      val op2 = cache.awaitAdd("hello", Value("changed"), 5.seconds)
      assertEquals(op2, false)

      val changed = cache.awaitGet[Value]("hello")
      assertEquals(changed, Some(Value("world")))
    }
  }

  test("add-null") {
    withFakeMemcached { cache =>
      intercept[NullPointerException] {
        cache.awaitAdd("hello", null, 5.seconds)
      }
    }
  }

  test("get") {
    withFakeMemcached { cache =>
      val value = cache.awaitGet[Value]("missing")
      assertEquals(value, None)
    }
  }

  test("set") {
    withFakeMemcached { cache =>
      assertEquals(cache.awaitGet[Value]("hello"), None)

      cache.awaitSet("hello", Value("world"), 3.seconds)
      assertEquals(cache.awaitGet[Value]("hello"), Some(Value("world")))

      cache.awaitSet("hello", Value("changed"), 3.seconds)
      assertEquals(cache.awaitGet[Value]("hello"), Some(Value("changed")))
    }
  }

  test("set-null") {
    withFakeMemcached { cache =>
      intercept[NullPointerException] {
        cache.awaitSet("hello", null, 5.seconds)
      }
    }
  }

  test("delete") {
    withFakeMemcached { cache =>
      cache.awaitDelete("hello")
      assertEquals(cache.awaitGet[Value]("hello"), None)

      cache.awaitSet("hello", Value("world"), 1.minute)
      assertEquals(cache.awaitGet[Value]("hello"), Some(Value("world")))

      assertEquals(cache.awaitDelete("hello"), true)
      assertEquals(cache.awaitGet[Value]("hello"), None)

      assertEquals(cache.awaitDelete("hello"), false)
    }
  }

  test("compareAndSet") {
    withFakeMemcached { cache =>
      cache.awaitDelete("some-key")
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      // no can do
      assertEquals(Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf), false)
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      // set to value1
      assert(Await.result(cache.compareAndSet("some-key", None, Value("value1"), 5.seconds), Duration.Inf))
      assertEquals(cache.awaitGet[Value]("some-key"), Some(Value("value1")))

      // no can do
      assert(!Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf))
      assertEquals(cache.awaitGet[Value]("some-key"), Some(Value("value1")))

      // set to value2, from value1
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("value1")), Value("value2"), 15.seconds), Duration.Inf))
      assertEquals(cache.awaitGet[Value]("some-key"), Some(Value("value2")))

      // no can do
      assert(!Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf))
      assertEquals(cache.awaitGet[Value]("some-key"), Some(Value("value2")))

      // set to value3, from value2
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("value2")), Value("value3"), 15.seconds), Duration.Inf))
      assertEquals(cache.awaitGet[Value]("some-key"), Some(Value("value3")))
    }
  }

  test("transformAndGet") {
    withFakeMemcached { cache =>
      cache.awaitDelete("some-key")
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      def incrementValue =
        cache.transformAndGet[Int]("some-key", 5.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      assert(Await.result(incrementValue, Duration.Inf) == 1)
      assert(Await.result(incrementValue, Duration.Inf) == 2)
      assert(Await.result(incrementValue, Duration.Inf) == 3)
      assert(Await.result(incrementValue, Duration.Inf) == 4)
      assert(Await.result(incrementValue, Duration.Inf) == 5)
      assert(Await.result(incrementValue, Duration.Inf) == 6)
    }
  }

  test("getAndTransform") {
    withFakeMemcached { cache =>
      cache.awaitDelete("some-key")
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      def incrementValue = Await.result(
        cache.getAndTransform[Int]("some-key", 5.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        },
        Duration.Inf
      )

      assertEquals(incrementValue, None)
      assertEquals(incrementValue, Some(1))
      assertEquals(incrementValue, Some(2))
      assertEquals(incrementValue, Some(3))
      assertEquals(incrementValue, Some(4))
      assertEquals(incrementValue, Some(5))
      assertEquals(incrementValue, Some(6))
    }
  }

  test("transformAndGet-concurrent") {
    withFakeMemcached { cache =>
      cache.awaitDelete("some-key")
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      def incrementValue(): CancelableFuture[Int] =
        cache.transformAndGet[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val futures: List[Future[Int]] = (0 until 500).map(nr => incrementValue()).toList
      val seq = Future.sequence(futures)
      Await.result(seq, 20.seconds)

      assertEquals(cache.awaitGet[Int]("some-key"), Some(500))
    }
  }

  test("getAndTransform-concurrent") {
    withFakeMemcached { cache =>
      cache.awaitDelete("some-key")
      assertEquals(cache.awaitGet[Value]("some-key"), None)

      def incrementValue =
        cache.getAndTransform[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val futures: List[Future[Option[Int]]] = (0 until 500).map(nr => incrementValue).toList
      val seq = Future.sequence(futures)
      Await.result(seq, 20.seconds)

      assertEquals(cache.awaitGet[Int]("some-key"), Some(500))
    }
  }

  test("increment-decrement") {
    withFakeMemcached { cache =>
      assertEquals(cache.awaitGet[Int]("hello"), None)

      cache.awaitSet("hello", 123, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(123))

      cache.awaitIncrementAndGet("hello", 1, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(124))

      cache.awaitDecrementAndGet("hello", 1, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(123))
    }
  }

  test("increment-decrement-delta") {
    withFakeMemcached { cache =>
      assertEquals(cache.awaitGet[Int]("hello"), None)

      cache.awaitSet("hello", 123, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(123))

      cache.awaitIncrementAndGet("hello", 5, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(128))

      cache.awaitDecrementAndGet("hello", 5, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(123))
    }
  }

  test("increment-default") {
    withFakeMemcached { cache =>
      assertEquals(cache.awaitGet[Int]("hello"), None)

      cache.awaitIncrementAndGet("hello", 1, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(0))

      cache.awaitIncrementAndGet("hello", 1, 0, 1.hour)
      assertEquals(cache.awaitGet[Int]("hello"), Some(1))
    }
  }

  test("increment-overflow") {
    withFakeMemcached { cache =>
      assert(cache.awaitIncrementAndGet("hello", 1, Long.MaxValue, 1.hour) == Long.MaxValue)
      assert(cache.awaitIncrementAndGet("hello", 1, 0, 1.hour) == Long.MinValue)
      assertEquals(cache.awaitGet[Long]("hello"), Some(Long.MinValue))
    }
  }

  test("decrement-underflow") {
    withFakeMemcached { cache =>
      assert(cache.awaitDecrementAndGet("hello", 1, Long.MinValue, 1.hour) == Long.MinValue)
      assert(cache.awaitDecrementAndGet("hello", 1, 0, 1.hour) == Long.MaxValue)
      assert(cache.awaitDecrementAndGet("hello", 1, 0, 1.hour) == Long.MaxValue - 1)
      assertEquals(cache.awaitGet[Long]("hello"), Some(Long.MaxValue - 1))
    }
  }

  test("big-instance-1") {
    withFakeMemcached { cache =>
      val impression = testModels.bigInstance
      cache.awaitSet(impression.uuid, impression, 60.seconds)
      assertEquals(cache.awaitGet[Impression](impression.uuid), Some(impression))
    }
  }

  test("big-instance-1-manual") {
    withFakeMemcached { cache =>
      val byteOut = new ByteArrayOutputStream()
      val objectOut = new ObjectOutputStream(byteOut)

      val impression = testModels.bigInstance
      objectOut.writeObject(impression)
      val byteArray = byteOut.toByteArray

      cache.awaitSet(impression.uuid, byteArray, 60.seconds)

      val inBytes = cache.awaitGet[Array[Byte]](impression.uuid)
      assert(inBytes.isDefined)
      assert(inBytes.get.length == byteArray.length)
    }
  }

  test("big-instance-2") {
    withFakeMemcached { cache =>
      val impression = testModels.bigInstance2
      cache.awaitSet(impression.uuid, impression, 60.seconds)
      assertEquals(cache.awaitGet[Impression](impression.uuid), Some(impression))
    }
  }

  test("big-instance-3") {
    withFakeMemcached { cache =>
      val impression = testModels.bigInstance
      val result = cache.set(impression.uuid, impression, 60.seconds) flatMap { _ =>
        cache.get[Impression](impression.uuid)
      }

      assertEquals(Await.result(result, Duration.Inf), Some(impression))
    }
  }
}
