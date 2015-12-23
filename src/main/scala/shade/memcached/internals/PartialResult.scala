package shade.memcached.internals

import monifu.concurrent.atomic.AtomicAny

import scala.concurrent.{ Future, Promise }
import scala.util.{ Success, Try }

sealed trait PartialResult[+T]
case class FinishedResult[T](result: Try[Result[T]]) extends PartialResult[T]
case class FutureResult[T](result: Future[Result[T]]) extends PartialResult[T]
case object NoResultAvailable extends PartialResult[Nothing]

final class MutablePartialResult[T] {
  def tryComplete(result: Try[Result[T]]): Boolean =
    _result.compareAndSet(NoResultAvailable, FinishedResult(result))

  def tryCompleteWith(result: Future[Result[T]]): Boolean =
    _result.compareAndSet(NoResultAvailable, FutureResult(result))

  def completePromise(key: String, promise: Promise[Result[T]]): Unit = {
    _result.get match {
      case FinishedResult(result) =>
        promise.tryComplete(result)
      case FutureResult(result) =>
        promise.tryCompleteWith(result)
      case NoResultAvailable =>
        promise.tryComplete(Success(FailedResult(key, IllegalCompleteStatus)))
    }
  }

  private[this] val _result =
    AtomicAny(NoResultAvailable: PartialResult[T])
}
