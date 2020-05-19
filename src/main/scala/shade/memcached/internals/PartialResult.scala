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

package shade.memcached.internals

import monix.execution.atomic.AtomicAny

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
        promise.completeWith(result)
      case NoResultAvailable =>
        promise.tryComplete(Success(FailedResult(key, IllegalCompleteStatus)))
    }
  }

  private[this] val _result =
    AtomicAny(NoResultAvailable: PartialResult[T])
}
