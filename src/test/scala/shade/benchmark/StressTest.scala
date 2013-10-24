package shade.benchmark

import akka.actor.{ActorLogging, Props, ActorSystem, Actor}
import shade.tests.MemcachedTestHelpers
import shade.memcached.FailureMode
import scala.concurrent.duration._
import scala.util.{Try, Random}
import scala.concurrent.atomic.Atomic

object StressTest extends App with MemcachedTestHelpers {
  val counterSuccess = Atomic(0L)
  val latencySuccess = Atomic(0L)
  val counterFailure = Atomic(0L)
  val latencyFailure = Atomic(0L)
  val globalStartTs = Atomic(0L)

  case class Value(nr: Int)

  case object Tick
  case object Init

  class TestActor(requestsPerSecond: Int) extends Actor with ActorLogging {
    val memcached = createCacheObject(
      context.system,
      "stress-test-actor",
      failureMode = Some(FailureMode.Cancel),
      opTimeout = Some(30.millis)
    )

    def initialize() {
      log.info("Initializing ...")
      var i = 0
      while (i < 2000) {
        val key = "test" + i.toString
        val value = i

        Try(memcached.awaitSet(key, Value(value), 1.hour))
        i += 1
      }
      log.info("Initializing done.")
    }

    def receive = {
      case Init =>
        initialize()

      case Tick =>
        log.info("Tick")

        if (globalStartTs.get == 0)
          globalStartTs.compareAndSet(0, System.currentTimeMillis())

        var i = 0
        while (i < requestsPerSecond) {
          val idx = Random.nextInt(4000)
          val key = "test" + idx.toString

          val startTS = System.currentTimeMillis()
          val future = memcached.get[Value](key)

          future.onSuccess {
            case _ =>
              val latency = System.currentTimeMillis() - startTS
              counterSuccess.increment
              latencySuccess.transform(_ + latency)
          }

          future.onFailure {
            case _ =>
              val latency = System.currentTimeMillis() - startTS
              counterFailure.increment
              latencyFailure.transform(_ + latency)
          }

          i+=1
        }
    }
  }

  val system = ActorSystem("default")
  implicit val ec = system.dispatcher
  val actor = system.actorOf(Props(new TestActor(requestsPerSecond = 40000)))

  actor ! Init

  val scheduledTask = system.scheduler.schedule(10.seconds, 1.second, actor, Tick)
  readLine()

  val totalTime = System.currentTimeMillis() - globalStartTs.get
  println()
  val total = counterSuccess.get + counterFailure.get
  println(s"Total processed: $total")
  println(s"Processed per second: ${total / BigDecimal(totalTime / 1000)}")
  val latencyPerReq = BigDecimal(latencySuccess.get + latencyFailure.get) / (counterSuccess.get + counterFailure.get)
  println(s"Total latency per request: $latencyPerReq")
  println()

  if (counterSuccess.get > 0) {
    println(s"Successful requests: ${counterSuccess.get}")
    val latencyS = BigDecimal(latencySuccess.get / counterSuccess.get)
    println(s"Successful latency per request: $latencyS")
    println()
  }

  if (counterFailure.get > 0) {
    val percent = (100 * BigDecimal(counterFailure.get * 100) / total).toLong.toDouble / 100
    println(s"Failed requests: ${counterFailure.get} ($percent%)")
    val latencyF = BigDecimal(latencyFailure.get / counterFailure.get)
    println(s"Failure latency per request: $latencyF")
  }

  scheduledTask.cancel()

  println("\nPRESS ANY KEY TO CONTINUE")
  readLine()

  system.shutdown()
}
