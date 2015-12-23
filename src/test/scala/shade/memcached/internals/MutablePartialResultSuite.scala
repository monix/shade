package shade.memcached.internals

import org.scalatest.FunSuite
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }

import scala.concurrent.{ Future, Promise }
import scala.util.Success

class MutablePartialResultSuite
    extends FunSuite
    with ScalaFutures
    with IntegrationPatience {

  def assertCompletePromise(toCheck: MutablePartialResult[Boolean], expected: Boolean): Unit = {
    val promise = Promise[Result[Boolean]]()
    toCheck.completePromise("key1", promise)
    whenReady(promise.future) {
      case SuccessfulResult(_, r) => assert(r == expected)
      case _ => fail("not successful")
    }
  }

  test("initial state") {
    val pResult = new MutablePartialResult[Boolean]
    val promise = Promise[Result[Boolean]]()
    pResult.completePromise("key1", promise)
    whenReady(promise.future) { r =>
      assert(r.isInstanceOf[FailedResult])
    }
  }

  test("#tryComplete on a fresh MutablePartialResult") {
    val pResult = new MutablePartialResult[Boolean]
    pResult.tryComplete(Success(SuccessfulResult("key1", false)))
    assertCompletePromise(toCheck = pResult, expected = false)
  }

  test("#tryComplete on a MutablePartialResult that has already been completed") {
    val pResult = new MutablePartialResult[Boolean]
    assert(pResult.tryComplete(Success(SuccessfulResult("key1", false))))
    assert(!pResult.tryComplete(Success(SuccessfulResult("key1", true))))
    assertCompletePromise(toCheck = pResult, expected = false)
  }

  test("#tryCompleteWith on a fresh MutablePartialResult") {
    val pResult = new MutablePartialResult[Boolean]
    pResult.tryCompleteWith(Future.successful(SuccessfulResult("key1", false)))
    assertCompletePromise(toCheck = pResult, expected = false)
  }

  test("#tryCompleteWith on a MutablePartialResult that has already been comppleted") {
    val pResult = new MutablePartialResult[Boolean]
    pResult.tryCompleteWith(Future.successful(SuccessfulResult("key1", false)))
    assert(!pResult.tryCompleteWith(Future.successful(SuccessfulResult("key1", false))))
    assertCompletePromise(toCheck = pResult, expected = false)
  }

}
