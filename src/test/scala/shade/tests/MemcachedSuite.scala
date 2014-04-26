package shade.tests

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.ExecutionContext.Implicits.global
import shade.testModels.{ContentPiece, Impression}
import java.io.{ObjectOutputStream, ByteArrayOutputStream}
import shade.memcached.FailureMode
import scala.concurrent.duration._
import scala.concurrent.Await
import shade.TimeoutException


@RunWith(classOf[JUnitRunner])
class MemcachedSuite extends FunSuite with MemcachedTestHelpers {
  implicit val timeout = 5.second

  test("add") {
    withCache("add") { cache =>
      val op1 = cache.awaitAdd("hello", Value("world"), 5.seconds)
      assert(op1 === true)

      val stored = cache.awaitGet[Value]("hello")
      assert(stored === Some(Value("world")))

      val op2 = cache.awaitAdd("hello", Value("changed"), 5.seconds)
      assert(op2 === false)

      val changed = cache.awaitGet[Value]("hello")
      assert(changed === Some(Value("world")))
    }
  }

  test("add-null") {
    withCache("add-null") { cache =>
      val op1 = cache.awaitAdd("hello", null, 5.seconds)
      assert(op1 === false)

      val stored = cache.awaitGet[Value]("hello")
      assert(stored === None)
    }
  }

  test("get") {
    withCache("get") { cache =>
      val value = cache.awaitGet[Value]("missing")
      assert(value === None)
    }
  }

  test("set") {
    withCache("set") { cache =>
      assert(cache.awaitGet[Value]("hello") === None)

      cache.awaitSet("hello", Value("world"), 1.seconds)
      assert(cache.awaitGet[Value]("hello") === Some(Value("world")))

      cache.awaitSet("hello", Value("changed"), 1.second)
      assert(cache.awaitGet[Value]("hello") === Some(Value("changed")))

      Thread.sleep(3000)

      assert(cache.awaitGet[Value]("hello") === None)
    }
  }

  test("set-null") {
    withCache("set-null") { cache =>
      val op1 = cache.awaitAdd("hello", null, 5.seconds)
      assert(op1 === false)

      val stored = cache.awaitGet[Value]("hello")
      assert(stored === None)
    }
  }

  test("delete") {
    withCache("delete") { cache =>
      cache.awaitDelete("hello")
      assert(cache.awaitGet[Value]("hello") === None)

      cache.awaitSet("hello", Value("world"), 1.minute)
      assert(cache.awaitGet[Value]("hello") === Some(Value("world")))

      assert(cache.awaitDelete("hello") === true)
      assert(cache.awaitGet[Value]("hello") === None)

      assert(cache.awaitDelete("hello") === false)
    }
  }

  test("compareAndSet") {
    withCache("compareAndSet") { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      // no can do
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf) === false)
      assert(cache.awaitGet[Value]("some-key") === None)

      // set to value1
      assert(Await.result(cache.compareAndSet("some-key", None, Value("value1"), 5.seconds), Duration.Inf) === true)
      assert(cache.awaitGet[Value]("some-key") === Some(Value("value1")))

      // no can do
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf) === false)
      assert(cache.awaitGet[Value]("some-key") === Some(Value("value1")))

      // set to value2, from value1
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("value1")), Value("value2"), 15.seconds), Duration.Inf) === true)
      assert(cache.awaitGet[Value]("some-key") === Some(Value("value2")))

      // no can do
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("invalid")), Value("value1"), 15.seconds), Duration.Inf) === false)
      assert(cache.awaitGet[Value]("some-key") === Some(Value("value2")))

      // set to value3, from value2
      assert(Await.result(cache.compareAndSet("some-key", Some(Value("value2")), Value("value3"), 15.seconds), Duration.Inf) === true)
      assert(cache.awaitGet[Value]("some-key") === Some(Value("value3")))
    }
  }

  test("transformAndGet") {
    withCache("transformAndGet") { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue =
        cache.transformAndGet[Int]("some-key", 5.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      assert(Await.result(incrementValue, Duration.Inf) === 1)
      assert(Await.result(incrementValue, Duration.Inf) === 2)
      assert(Await.result(incrementValue, Duration.Inf) === 3)
      assert(Await.result(incrementValue, Duration.Inf) === 4)
      assert(Await.result(incrementValue, Duration.Inf) === 5)
      assert(Await.result(incrementValue, Duration.Inf) === 6)
    }
  }

  test("getAndTransform") {
    withCache("getAndTransform") { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue = Await.result(
        cache.getAndTransform[Int]("some-key", 5.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        },
        Duration.Inf
      )

      assert(incrementValue === None)
      assert(incrementValue === Some(1))
      assert(incrementValue === Some(2))
      assert(incrementValue === Some(3))
      assert(incrementValue === Some(4))
      assert(incrementValue === Some(5))
      assert(incrementValue === Some(6))
    }
  }

  test("transformAndGet-concurrent") {
    withCache("transformAndGet", opTimeout = Some(10.seconds)) { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue =
        cache.transformAndGet[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val seq = concurrent.Future.sequence((0 until 100).map(nr => incrementValue))
      Await.result(seq, 20.seconds)

      assert(cache.awaitGet[Int]("some-key") === Some(100))
    }
  }

  test("getAndTransform-concurrent") {
    withCache("getAndTransform", opTimeout = Some(10.seconds)) { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue =
        cache.getAndTransform[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val seq = concurrent.Future.sequence((0 until 100).map(nr => incrementValue))
      Await.result(seq, 20.seconds)

      assert(cache.awaitGet[Int]("some-key") === Some(100))
    }
  }

  test("transformAndGet-concurrent-timeout") {
    withCache("transformAndGet", opTimeout = Some(300.millis)) { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue =
        cache.transformAndGet[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val initial = Await.result(incrementValue.flatMap { case _ => incrementValue }, 3.seconds)
      assert(initial === 2)

      val seq = concurrent.Future.sequence((0 until 500).map(nr => incrementValue))
      try {
        Await.result(seq, 20.seconds)
        fail("should throw exception")
      }
      catch {
        case ex: TimeoutException =>
          assert(ex.getMessage === "some-key")
      }
    }
  }

  test("getAndTransform-concurrent-timeout") {
    withCache("getAndTransform", opTimeout = Some(300.millis)) { cache =>
      cache.awaitDelete("some-key")
      assert(cache.awaitGet[Value]("some-key") === None)

      def incrementValue =
        cache.getAndTransform[Int]("some-key", 60.seconds) {
          case None => 1
          case Some(nr) => nr + 1
        }

      val initial = Await.result(incrementValue.flatMap { case _ => incrementValue }, 3.seconds)
      assert(initial === Some(1))

      val seq = concurrent.Future.sequence((0 until 500).map(nr => incrementValue))

      try {
        Await.result(seq, 20.seconds)
        fail("should throw exception")
      }
      catch {
        case ex: TimeoutException =>
          assert(ex.key === "some-key")
      }
    }
  }

  test("vector-inherited-case-classes") {
    withCache("vector-inherited-case-classes") { cache =>
      val content = shade.testModels.contentSeq
      cache.awaitSet("blog-posts", content, 60.seconds)
      assert(cache.awaitGet[Vector[ContentPiece]]("blog-posts") === Some(content))
    }
  }

  test("big-instance-1") {
    withCache("big-instance-1") { cache =>
      val impression = shade.testModels.bigInstance
      cache.awaitSet(impression.uuid, impression, 60.seconds)
      assert(cache.awaitGet[Impression](impression.uuid) === Some(impression))
    }
  }

  test("big-instance-1-manual") {
    withCache("big-instance-1-manual") { cache =>
      val byteOut = new ByteArrayOutputStream()
      val objectOut = new ObjectOutputStream(byteOut)

      val impression = shade.testModels.bigInstance
      objectOut.writeObject(impression)
      val byteArray = byteOut.toByteArray

      cache.awaitSet(impression.uuid, byteArray, 60.seconds)

      val inBytes = cache.awaitGet[Array[Byte]](impression.uuid)
      assert(inBytes.isDefined)
      assert(inBytes.get.length == byteArray.length)
    }
  }


  test("big-instance-2") {
    withCache("big-instance-2") { cache =>
      val impression = shade.testModels.bigInstance2
      cache.awaitSet(impression.uuid, impression, 60.seconds)
      assert(cache.awaitGet[Impression](impression.uuid) === Some(impression))
    }
  }

  test("big-instance-3") {
    withCache("big-instance-3") { cache =>
      val impression = shade.testModels.bigInstance
      val result = cache.set(impression.uuid, impression, 60.seconds) flatMap { _ =>
        cache.get[Impression](impression.uuid)
      }

      assert(Await.result(result, Duration.Inf) === Some(impression))
    }
  }

  test("cancel-strategy simple test") {
    withCache("cancel-strategy", failureMode = Some(FailureMode.Cancel)) { cache =>
      Thread.sleep(100)
      val impression = shade.testModels.bigInstance2
      cache.awaitSet(impression.uuid, impression, 60.seconds)
      assert(cache.awaitGet[Impression](impression.uuid) === Some(impression))
    }
  }
}
