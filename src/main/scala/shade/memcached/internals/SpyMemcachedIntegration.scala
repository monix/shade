package shade.memcached.internals

import java.net.{SocketAddress, InetSocketAddress}
import net.spy.memcached.compat.SpyObject
import net.spy.memcached.ops._
import net.spy.memcached._
import collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Try, Failure, Success}
import akka.actor.Scheduler
import scala.util.control.NonFatal
import net.spy.memcached.auth.AuthThreadMonitor
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.io.IOException
import shade.UnhandledStatusException
import scala.concurrent.atomic.Atomic


/**
 * @param cf is Spy's Memcached connection factory
 * @param addrs is a list of addresses to connect to
 * @param scheduler is for making timeouts work
 */
class SpyMemcachedIntegration(cf: ConnectionFactory, addrs: Seq[InetSocketAddress], scheduler: Scheduler)
  extends SpyObject with ConnectionObserver {

  require(cf != null, "Invalid connection factory")
  require(addrs != null && !addrs.isEmpty, "Invalid addresses list")
  assert(cf.getOperationTimeout > 0, "Operation timeout must be positive")

  protected final val opFact = cf.getOperationFactory
  protected final val mconn = cf.createConnection(addrs.asJava)
  protected final val authDescriptor = Option(cf.getAuthDescriptor)
  protected final val authMonitor: AuthThreadMonitor = new AuthThreadMonitor
  protected final val shuttingDown = Atomic(initialValue = false)

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
      for (node <- mconn.getLocator.getAll.asScala)
        if (node.isActive)
          obs.connectionEstablished(node.getSocketAddress, -1)
    rv
  }

  def connectionLost(sa: SocketAddress) {
    // Don't care?
  }

  /**
   * A connection has just successfully been established on the given socket.
   *
   * @param sa the address of the node whose connection was established
   * @param reconnectCount the number of attempts before the connection was
   *                       established
   */
  def connectionEstablished(sa: SocketAddress, reconnectCount: Int) {
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
          def complete() {
            latch.countDown()
          }

          def receivedStatus(s: OperationStatus) {}
        })
      }
    }, mconn.getLocator.getAll, checkShuttingDown = false)

    try {
      blatch.await(timeout, unit)
    }
    catch {
      case e: InterruptedException => {
        throw new RuntimeException("Interrupted waiting for queues", e)
      }
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
    if (checkShuttingDown && shuttingDown.get)
      throw new IllegalStateException("Shutting down")
    mconn.broadcastOperation(of, nodes)
  }

  private def findNode(sa: SocketAddress): MemcachedNode = {
    val node = mconn.getLocator.getAll.asScala.find(_.getSocketAddress == sa)
    assert(node.isDefined, "Couldn't find node connected to " + sa)
    node.get
  }

  /**
   * Shut down immediately.
   */
  def shutdown() {
    shutdown(-1, TimeUnit.SECONDS)
  }

  def shutdown(timeout: Long, unit: TimeUnit): Boolean = {
    // Guard against double shutdowns (bug 8).
    if (!shuttingDown.compareAndSet(expect = false, update = true)) {
      getLogger.info("Suppressing duplicate attempt to shut down")
      false
    }
    else {
      val baseName: String = mconn.getName
      mconn.setName(baseName + " - SHUTTING DOWN")

      try {
        if (timeout > 0) {
          mconn.setName(baseName + " - SHUTTING DOWN (waiting)")
          waitForQueues(timeout, unit)
        }
        else
          true
      }
      finally {
        try {
          mconn.setName(baseName + " - SHUTTING DOWN (telling client)")
          mconn.shutdown()
          mconn.setName(baseName + " - SHUTTING DOWN (informed client)")
        }
        catch {
          case e: IOException =>
            getLogger.warn("exception while shutting down": Any, e: Throwable)
        }
      }
    }
  }

  def realAsyncGet(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Option[Array[Byte]]]] = {
    val promise = Promise[Result[Option[Array[Byte]]]]()
    val result = new MutablePartialResult[Option[Array[Byte]]]

    val op: GetOperation = opFact.get(key, new GetOperation.Callback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASNotFoundStatus =>
              result.tryComplete(Success(SuccessfulResult(key, None)))
            case CASSuccessStatus =>
            // nothing
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }

        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def gotData(k: String, flags: Int, data: Array[Byte]) {
        assert(key == k, "Wrong key returned")
        result.tryComplete(Success(SuccessfulResult(key, Option(data))))
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncSet(key: String, data: Array[Byte], flags: Int, exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Long]] = {
    val promise = Promise[Result[Long]]()
    val result = new MutablePartialResult[Long]

    val op: Operation = opFact.store(StoreType.set, key, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASSuccessStatus =>
            // nothing
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }

        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def gotData(key: String, cas: Long) {
        result.tryComplete(Success(SuccessfulResult(key, cas)))
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncAdd(key: String, data: Array[Byte], flags: Int, exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Option[Long]]] = {
    val promise = Promise[Result[Option[Long]]]()
    val result = new MutablePartialResult[Option[Long]]

    val op: Operation = opFact.store(StoreType.add, key, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASExistsStatus =>
              result.tryComplete(Success(SuccessfulResult(key, None)))
            case CASSuccessStatus =>
              // nothing
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }
        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def gotData(key: String, cas: Long) {
        result.tryComplete(Success(SuccessfulResult(key, Some(cas))))
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncDelete(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Boolean]] = {
    val promise = Promise[Result[Boolean]]()
    val result = new MutablePartialResult[Boolean]

    val op = opFact.delete(key, new OperationCallback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASSuccessStatus =>
              result.tryComplete(Success(SuccessfulResult(key, true)))
            case CASNotFoundStatus =>
              result.tryComplete(Success(SuccessfulResult(key, false)))
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }

        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncGets(key: String, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Option[(Array[Byte], Long)]]] = {
    val promise = Promise[Result[Option[(Array[Byte], Long)]]]()
    val result = new MutablePartialResult[Option[(Array[Byte], Long)]]

    val op: Operation = opFact.gets(key, new GetsOperation.Callback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASNotFoundStatus =>
              result.tryComplete(Success(SuccessfulResult(key, None)))
            case CASSuccessStatus =>
            // nothing
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }
        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def gotData(receivedKey: String, flags: Int, cas: Long, data: Array[Byte]) {
        assert(key == receivedKey, "Wrong key returned")
        assert(cas > 0, "CAS was less than zero:  " + cas)

        result.tryComplete(Try {
          SuccessfulResult(key, Option(data).map(d => (d, cas)))
        })
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  def realAsyncCAS(key: String, casID: Long, flags: Int, data: Array[Byte], exp: Duration, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[Boolean]] = {
    val promise = Promise[Result[Boolean]]()
    val result = new MutablePartialResult[Boolean]

    val op = opFact.cas(StoreType.set, key, casID, flags, expiryToSeconds(exp).toInt, data, new StoreOperation.Callback {
      def receivedStatus(opStatus: OperationStatus) {
        if (statusTranslation.isDefinedAt(opStatus))
          statusTranslation(opStatus) match {
            case CASSuccessStatus =>
              result.tryComplete(Success(SuccessfulResult(key, true)))
            case CASExistsStatus =>
              result.tryComplete(Success(SuccessfulResult(key, false)))
            case CASNotFoundStatus =>
              result.tryComplete(Success(SuccessfulResult(key, false)))
            case failure =>
              result.tryComplete(Success(FailedResult(key, failure)))
          }

        else
          throw new UnhandledStatusException(
            "%s(%s)".format(opStatus.getClass, opStatus.getMessage))
      }

      def gotData(k: String, cas: Long) {
        assert(key == k, "Wrong key returned")
      }

      def complete() {
        result.completePromise(key, promise)
      }
    })

    mconn.enqueueOperation(key, op)
    prepareFuture(key, op, promise, timeout)
  }

  protected final def prepareFuture[T](key: String, op: Operation, promise: Promise[Result[T]], atMost: FiniteDuration)(implicit ec: ExecutionContext): Future[Result[T]] = {
    val cancellable = scheduler.scheduleOnce(atMost) {
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

    future.onComplete {
      case msg =>
        try
          if (!cancellable.isCancelled)
            cancellable.cancel()
        catch {
          case NonFatal(_) =>
        }

        msg match {
          case Success(FailedResult(_, TimedOutStatus)) =>
            MemcachedConnection.opTimedOut(op)
            op.timeOut()
            if (!op.isCancelled) try op.cancel() catch { case NonFatal(_) => }
          case Success(FailedResult(_, CancelledStatus | IllegalCompleteStatus)) =>
            if (!op.isCancelled) try op.cancel() catch { case NonFatal(_) => }
          case _ =>
            MemcachedConnection.opSucceeded(op)
        }
    }

    future
  }

  protected final def statusTranslation: PartialFunction[OperationStatus, Status] = {
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
      }
  }

  protected final def expiryToSeconds(duration: Duration): Long = duration match {
    case finite: FiniteDuration =>
      val seconds = finite.toSeconds
      if (seconds < 60 * 60 * 24 * 30)
        seconds
      else
        System.currentTimeMillis() / 1000 + seconds

    // infinite duration (set to 365 days)
    case _ =>
      System.currentTimeMillis() / 1000 + 31536000 // 60 * 60 * 24 * 365 -> 365 days in seconds
  }
}
