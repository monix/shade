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

import java.io.IOException
import java.net.{ InetSocketAddress, SocketAddress }
import java.util.concurrent.{ CountDownLatch, TimeUnit }

import monix.execution.{ Cancelable, CancelableFuture, Scheduler }
import monix.execution.atomic.{ Atomic, AtomicBoolean }
import net.spy.memcached._
import net.spy.memcached.auth.{ AuthDescriptor, AuthThreadMonitor }
import net.spy.memcached.compat.SpyObject
import net.spy.memcached.ops._
import shade.UnhandledStatusException

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

/**
 * Hooking in the SpyMemcached Internals.
 *
 * @param cf is Spy's Memcached connection factory
 * @param addrs is a list of addresses to connect to
 * @param scheduler is for making timeouts work
 */
class SpyMemcachedIntegration(cf: ConnectionFactory, addrs: Seq[InetSocketAddress], scheduler: Scheduler)
  extends SpyObject with ConnectionObserver {

  require(cf != null, "Invalid connection factory")
  require(addrs != null && addrs.nonEmpty, "Invalid addresses list")
  assert(cf.getOperationTimeout > 0, "Operation timeout must be positive")

  protected final val opFact: OperationFactory = cf.getOperationFactory
  protected final val mconn: MemcachedConnection = cf.createConnection(addrs.asJava)
  protected final val authDescriptor: Option[AuthDescriptor] = Option(cf.getAuthDescriptor)
  protected final val authMonitor: AuthThreadMonitor = new AuthThreadMonitor
  protected final val shuttingDown: AtomicBoolean = Atomic(false)

  locally {
    if (authDescriptor.isDefined)
      addObserver(this)
  }

  /**
   * Add a connection observer.
   *
   * If connections are already established, your observer will be called with
   * the address and -1.
   *
   * @param obs the ConnectionObserver you wish to add
   * @return true if the observer was added.
   */
  def addObserver(obs: ConnectionObserver): Boolean = {
    val rv = mconn.addObserver(obs)

    if (rv)
      for (node <- mconn.getLocator.getAll().asScala)
        if (node.isActive)
          obs.connectionEstablished(node.getSocketAddress, -1)
    rv
  }

  def connectionLost(sa: SocketAddress): Unit = {
    // Don't care?
  }

  /**
   * A connection has just successfully been established on the given socket.
   *
   * @param sa the address of the node whose connection was established
   * @param reconnectCount the number of attempts before the connection was
   *                       established
   */
  def connectionEstablished(sa: SocketAddress, reconnectCount: Int): Unit = {
    for (authDescriptor <- this.authDescriptor) {
      if (authDescriptor.authThresholdReached)
        this.shutdown()
      authMonitor.authConnection(mconn, opFact, authDescriptor, findNode(sa))
    }
  }

  /**
   * Wait for the queues to die down.
   *
   * @param timeout the amount of time time for shutdown
   * @param unit the TimeUnit for the timeout
   * @return result of the request for the wait
   * @throws IllegalStateException in the rare circumstance where queue is too
   *                               full to accept any more requests
   */
  def waitForQueues(timeout: Long, unit: TimeUnit): Boolean = {
    val blatch: CountDownLatch = broadcastOp(new BroadcastOpFactory {
      def newOp(n: MemcachedNode, latch: CountDownLatch): Operation = {
        opFact.noop(new OperationCallback {
          def complete(): Unit = {
            latch.countDown()
          }

          def receivedStatus(s: OperationStatus): Unit = {}
        })
      }
    }, mconn.getLocator.getAll, checkShuttingDown = false)

    try {
      blatch.await(timeout, unit)
    } catch {
      case e: InterruptedException =>
        throw new RuntimeException("Interrupted waiting for queues", e)
    }
  }

  def broadcastOp(of: BroadcastOpFactory): CountDownLatch =
    broadcastOp(of, mconn.getLocator.getAll, checkShuttingDown = true)

  def broadcastOp(of: BroadcastOpFactory, nodes: java.util.Collection[MemcachedNode]): CountDownLatch =
    broadcastOp(of, nodes, checkShuttingDown = true)

  /**
   * Broadcast an operation to a specific collection of nodes.
   */
  private def broadcastOp(of: BroadcastOpFactory, nodes: java.util.Collection[MemcachedNode], checkShuttingDown: Boolean): CountDownLatch = {
    if (checkShuttingDown && shuttingDown.get())
      throw new IllegalStateException("Shutting down")
    mconn.broadcastOperation(of, nodes)
  }

  private def findNode(sa: SocketAddress): MemcachedNode = {
    val node = mconn.getLocator.getAll().asScala.find(_.getSocketAddress == sa)
    assert(node.isDefined, s"Couldn't find node connected to $sa")
    node.get
  }

  /**
   * Shut down immediately.
   */
  def shutdown(): Unit = {
    shutdown(-1, TimeUnit.SECONDS)
  }

  def shutdown(timeout: Long, unit: TimeUnit): Boolean = {
    // Guard against double shutdowns (bug 8).
    if (!shuttingDown.compareAndSet(expect = false, update = true)) {
      getLogger.info("Suppressing duplicate attempt to shut down", Nil)
      false
    } else {
      val baseName: String = mconn.getName
      mconn.setName(s"$baseName - SHUTTING DOWN")

      try {
        if (timeout > 0) {
          mconn.setName(s"$baseName - SHUTTING DOWN (waiting)")
          waitForQueues(timeout, unit)
        } else
          true
      } finally {
        try {
          mconn.setName(s"$baseName - SHUTTING DOWN (telling client)")
          mconn.shutdown()
          mconn.setName(s"$baseName - SHUTTING DOWN (informed client)")
        } catch {
          case e: IOException =>
            getLogger.warn("exception while shutting down": Any, e: Throwable)
        }
      }
    }
  }

  def realAsyncGet(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Option[Array[Byte]]]] = {
    val promise = Promise[Result[Option[Array[Byte]]]]()
    val result = new MutablePartialResult[Option[Array[Byte]]]

    val op: GetOperation = opFact.get(key, new GetOperation.Callback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASNotFoundStatus =>
            result.tryComplete(Success(SuccessfulResult(key, None)))
          case CASSuccessStatus =>
        }
      }

      def gotData(k: String, flags: Int, data: Array[Byte]): Unit = {
        assert(key == k, "Wrong key returned")
        result.tryComplete(Success(SuccessfulResult(key, Option(data))))
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncSet(key: String, data: Array[Byte], flags: Int, exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Long]] = {
    val promise = Promise[Result[Long]]()
    val result = new MutablePartialResult[Long]

    val op: Operation = opFact.store(StoreType.set, key, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASSuccessStatus =>
        }
      }

      def gotData(key: String, cas: Long): Unit = {
        result.tryComplete(Success(SuccessfulResult(key, cas)))
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncAdd(key: String, data: Array[Byte], flags: Int, exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Option[Long]]] = {
    val promise = Promise[Result[Option[Long]]]()
    val result = new MutablePartialResult[Option[Long]]

    val op: Operation = opFact.store(StoreType.add, key, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASExistsStatus =>
            result.tryComplete(Success(SuccessfulResult(key, None)))
          case CASSuccessStatus =>
        }
      }

      def gotData(key: String, cas: Long): Unit = {
        result.tryComplete(Success(SuccessfulResult(key, Some(cas))))
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncDelete(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Boolean]] = {
    val promise = Promise[Result[Boolean]]()
    val result = new MutablePartialResult[Boolean]

    val op = opFact.delete(key, new DeleteOperation.Callback {
      def gotData(cas: Long): Unit = ()

      def complete(): Unit = {
        result.completePromise(key, promise)
      }

      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASSuccessStatus =>
            result.tryComplete(Success(SuccessfulResult(key, true)))
          case CASNotFoundStatus =>
            result.tryComplete(Success(SuccessfulResult(key, false)))
        }
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncGets(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Option[(Array[Byte], Long)]]] = {
    val promise = Promise[Result[Option[(Array[Byte], Long)]]]()
    val result = new MutablePartialResult[Option[(Array[Byte], Long)]]

    val op: Operation = opFact.gets(key, new GetsOperation.Callback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASNotFoundStatus =>
            result.tryComplete(Success(SuccessfulResult(key, None)))
          case CASSuccessStatus =>
        }
      }

      def gotData(receivedKey: String, flags: Int, cas: Long, data: Array[Byte]): Unit = {
        assert(key == receivedKey, "Wrong key returned")
        assert(cas > 0, s"CAS was less than zero:  $cas")

        result.tryComplete(Try {
          SuccessfulResult(key, Option(data).map(d => (d, cas)))
        })
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncCAS(key: String, casID: Long, flags: Int, data: Array[Byte], exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Boolean]] = {
    val promise = Promise[Result[Boolean]]()
    val result = new MutablePartialResult[Boolean]

    val op = opFact.cas(StoreType.set, key, casID, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASSuccessStatus =>
            result.tryComplete(Success(SuccessfulResult(key, true)))
          case CASExistsStatus =>
            result.tryComplete(Success(SuccessfulResult(key, false)))
          case CASNotFoundStatus =>
            result.tryComplete(Success(SuccessfulResult(key, false)))
        }
      }

      def gotData(k: String, cas: Long): Unit = {
        assert(key == k, "Wrong key returned")
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncMutate(key: String, by: Long, mutator: Mutator, default: Option[Long], exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[Long]] = {
    val promise = Promise[Result[Long]]()
    val result = new MutablePartialResult[Long]

    val expiry = default match {
      case Some(_) => expiryToSeconds(exp).toInt
      case None => -1 // expiry of all 1-bits disables setting default in case of nonexistent key
    }

    val op: Operation = opFact.mutate(mutator, key, by, default.getOrElse(0L), expiry, new OperationCallback {
      def receivedStatus(opStatus: OperationStatus): Unit = {
        handleStatus(opStatus, key, result) {
          case CASSuccessStatus =>
            result.tryComplete(Success(SuccessfulResult(key, opStatus.getMessage.toLong)))
        }
      }

      def complete(): Unit = {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  protected final def prepareFuture[T](key: String, op: Operation, promise: Promise[Result[T]], atMost: FiniteDuration)(implicit ec: ExecutionContext): CancelableFuture[Result[T]] = {
    val operationCancelable = Cancelable(() => {
      try {
        if (!op.isCancelled)
          op.cancel()
      } catch {
        case NonFatal(ex) =>
          ec.reportFailure(ex)
      }
    })

    val timeout = scheduler.scheduleOnce(atMost) {
      promise.tryComplete {
        if (op.hasErrored)
          Failure(op.getException)
        else if (op.isCancelled)
          Success(FailedResult(key, CancelledStatus))
        else
          Success(FailedResult(key, TimedOutStatus))
      }
    }

    val future = promise.future
    val mainCancelable = Cancelable { () =>
      timeout.cancel()
      operationCancelable.cancel()
    }

    future.onComplete { msg =>
      try {
        timeout.cancel()
      } catch {
        case NonFatal(ex) =>
          ec.reportFailure(ex)
      }

      msg match {
        case Success(FailedResult(_, TimedOutStatus)) =>
          MemcachedConnection.opTimedOut(op)
          op.timeOut()
          if (!op.isCancelled) try op.cancel() catch {
            case NonFatal(_) =>
          }
        case Success(FailedResult(_, _)) =>
          if (!op.isCancelled) try op.cancel() catch {
            case NonFatal(_) =>
          }
        case _ =>
          MemcachedConnection.opSucceeded(op)
      }
    }

    CancelableFuture(future, mainCancelable)
  }

  protected final val statusTranslation: PartialFunction[OperationStatus, Status] = {
    case _: CancelledOperationStatus =>
      CancelledStatus
    case _: TimedOutOperationStatus =>
      TimedOutStatus
    case status: CASOperationStatus =>
      status.getCASResponse match {
        case CASResponse.EXISTS =>
          CASExistsStatus
        case CASResponse.NOT_FOUND =>
          CASNotFoundStatus
        case CASResponse.OK =>
          CASSuccessStatus
        case CASResponse.OBSERVE_ERROR_IN_ARGS =>
          CASObserveErrorInArgs
        case CASResponse.OBSERVE_MODIFIED =>
          CASObserveModified
        case CASResponse.OBSERVE_TIMEOUT =>
          CASObserveTimeout
      }
    case x if x.isSuccess =>
      CASSuccessStatus
  }

  protected final def expiryToSeconds(duration: Duration): Long = duration match {
    case finite: FiniteDuration =>
      val seconds = finite.toSeconds
      if (seconds < 60 * 60 * 24 * 30)
        seconds
      else
        System.currentTimeMillis() / 1000 + seconds
    case _ =>
      // infinite duration (set to 0)
      0
  }

  /**
   * Handles OperationStatuses from SpyMemcached
   *
   * The first argument list takes the SpyMemcached operation status, and also the key and result so that this method
   * itself can attach sane failure handling.
   *
   * The second argument list is a simple PartialFunction that allows you to side effect for the translated [[Status]]s you care about,
   * typically by completing the result.
   *
   * @param spyMemcachedStatus SpyMemcached OperationStatus to be translated
   * @param key String key involved in the operation
   * @param result MutablePartialResult
   * @param handler a partial function that takes a translated [[Status]] and side-effects
   */
  private def handleStatus(
    spyMemcachedStatus: OperationStatus,
    key: String,
    result: MutablePartialResult[_])(handler: PartialFunction[Status, Unit]): Unit = {
    val status = statusTranslation.applyOrElse(spyMemcachedStatus, UnhandledStatus.fromSpyMemcachedStatus)
    handler.applyOrElse(status, {
      case UnhandledStatus(statusClass, statusMsg) => result.tryComplete(Failure(new UnhandledStatusException(s"$statusClass($statusMsg)")))
      // nothing
      case failure =>
        result.tryComplete(Success(FailedResult(key, failure)))
    }: Function[Status, Unit])
  }
}
