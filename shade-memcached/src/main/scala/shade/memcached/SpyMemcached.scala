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

import monix.eval.{Callback, Task}
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import java.lang.{Boolean => JavaBoolean, Long => JavaLong}
import java.util.concurrent.{Future => JavaFuture}

import net.spy.memcached.internal._
import net.spy.memcached.ConnectionFactoryBuilder.{Protocol => SpyProtocol}
import net.spy.memcached.auth.{AuthDescriptor, PlainCallbackHandler}
import net.spy.memcached.{AddrUtil, CASResponse, ConnectionFactoryBuilder, MemcachedClient, FailureMode => SpyFailureMode}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Promise}
import scala.language.higherKinds
import scala.util.Try
import scala.util.control.NonFatal

class SpyMemcached(config: Configuration) extends Memcached {
  override def addL[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Task[Boolean] =
    triggerOperationTask[JavaBoolean, Boolean, OperationFuture](
      javaToScalaBoolean,
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.add(withPrefix(key), expSecs, value, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def add[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean] = {

    triggerOperationFuture[JavaBoolean, Boolean, OperationFuture](
      javaToScalaBoolean,
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.add(withPrefix(key), expSecs, value, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def setL[T](key: String, value: T, exp: Duration)(implicit codec: Codec[T]): Task[Unit] =
    triggerOperationTask[JavaBoolean, Unit, OperationFuture](
      unit,
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.set(withPrefix(key), expSecs, value, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def set[T](key: String, value: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Unit] = {

    triggerOperationFuture[JavaBoolean, Unit, OperationFuture](
      unit,
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.set(withPrefix(key), expSecs, value, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def deleteL(key: String): Task[Boolean] =
    triggerOperationTask[JavaBoolean, Boolean,OperationFuture](
      javaToScalaBoolean,
      () => client.delete(withPrefix(key)),
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def delete(key: String)
    (implicit ec: ExecutionContext): CancelableFuture[Boolean] = {

    triggerOperationFuture[JavaBoolean, Boolean, OperationFuture](
      javaToScalaBoolean,
      () => client.delete(withPrefix(key)),
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def getL[T](key: String)(implicit codec: Codec[T]): Task[Option[T]] =
    triggerOperationTask[T, Option[T], GetFuture](
      (x: T) => Option(x),
      () => client.asyncGet(withPrefix(key), codec),
      (async, callback) => {
        async.addListener(new GetCompletionListener {
          def onComplete(future: GetFuture[_]) =
            callback()
        })
      }
    )

  override def get[T](key: String)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[T]] = {

    triggerOperationFuture[T, Option[T], GetFuture](
      (x: T) => Option(x),
      () => client.asyncGet(withPrefix(key), codec),
      (async, callback) => {
        async.addListener(new GetCompletionListener {
          def onComplete(future: GetFuture[_]) =
            callback()
        })
      }
    )
  }

  override def getsL[T](key: String)(implicit codec: Codec[T]): Task[Option[CASValue[T]]] =
    triggerOperationTask[CASValue[T], Option[CASValue[T]], OperationFuture](
      (x: CASValue[T]) => Option(x),
      () => client.asyncGets(withPrefix(key), codec),
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def gets[T](key: String)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Option[CASValue[T]]] = {

    triggerOperationFuture[CASValue[T], Option[CASValue[T]], OperationFuture](
      (x: CASValue[T]) => Option(x),
      () => client.asyncGets(withPrefix(key), codec),
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def rawCompareAndSetL[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T]): Task[Boolean] = {

    triggerOperationTask[CASResponse, Boolean, OperationFuture](
      {
        case CASResponse.OK | CASResponse.OBSERVE_MODIFIED => true
        case _ => false
      },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncCAS(withPrefix(key), casId, expSecs, update, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def rawCompareAndSet[T](key: String, casId: Long, update: T, exp: Duration)
    (implicit codec: Codec[T], ec: ExecutionContext): CancelableFuture[Boolean] = {

    triggerOperationFuture[CASResponse, Boolean, OperationFuture](
      {
        case CASResponse.OK | CASResponse.OBSERVE_MODIFIED => true
        case _ => false
      },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncCAS(withPrefix(key), casId, expSecs, update, codec)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def incrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long] =
    triggerOperationTask[JavaLong, Long, OperationFuture](
      { case null => 0L; case nr => nr },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncIncr(key, by, default, expSecs)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def incrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long] = {

    triggerOperationFuture[JavaLong, Long, OperationFuture](
      { case null => 0L; case nr => nr },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncIncr(key, by, default, expSecs)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  override def decrementAndGetL(key: String, by: Long, default: Long, exp: Duration): Task[Long] =
    triggerOperationTask[JavaLong, Long, OperationFuture](
      { case null => 0L; case nr => nr },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncDecr(key, by, default, expSecs)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )

  override def decrementAndGet(key: String, by: Long, default: Long, exp: Duration)
    (implicit ec: ExecutionContext): CancelableFuture[Long] = {

    triggerOperationFuture[JavaLong, Long, OperationFuture](
      { case null => 0L; case nr => nr },
      () => {
        val expSecs = expiryToSeconds(exp).toInt
        client.asyncDecr(key, by, default, expSecs)
      },
      (async, callback) => {
        async.addListener(new OperationCompletionListener {
          def onComplete(future: OperationFuture[_]): Unit =
            callback()
        })
      }
    )
  }

  /** Helper that converts anything to Unit. */
  private[this] val unit: (Any => Unit) =
    _ => ()

  /** Helper for converting Java's boxed booleans to Scala. */
  private[this] val javaToScalaBoolean: (JavaBoolean => Boolean) = {
    case null => false
    case b => b.booleanValue()
  }

  private def triggerOperationFuture[A, R, Async[T] <: JavaFuture[T]]
    (map: A => R, trigger: () => Async[A], addListener: (Async[A], () => Unit) => Unit)
    (implicit ec: ExecutionContext): CancelableFuture[R] = {

    try {
      val op = trigger()
      if (op.isDone) {
        CancelableFuture.fromTry(Try(map(op.get())))
      } else {
        val p = Promise[R]()
        addListener(op, () => p.complete(Try(map(op.get()))))

        CancelableFuture(p.future, Cancelable { () =>
          try op.cancel(false)
          catch { case NonFatal(ex) => ec.reportFailure(ex) }
        })
      }
    } catch {
      case NonFatal(ex) =>
        CancelableFuture.failed(ex)
    }
  }

  private def triggerOperationTask[A, B, Async[T] <: JavaFuture[T]]
    (map: A => B, trigger: () => Async[A], addListener: (Async[A], () => Unit) => Unit): Task[B] = {

    @inline def invoke(op: JavaFuture[A], cb: Callback[B], map: A => B)
      (implicit s: Scheduler): Unit = {

      var streamErrors = true
      try {
        val r = map(op.get())
        streamErrors = false
        cb.asyncOnSuccess(r)
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.asyncOnError(ex)
          else s.reportFailure(ex)
      }
    }

    Task.unsafeCreate[B] { (ctx, cb) =>
      implicit val s = ctx.scheduler
      var streamErrors = true
      try {
        val op = trigger()
        streamErrors = false

        // Fast path?
        if (op.isDone)
          s.executeTrampolined(() => invoke(op, cb, map))
        else {
          ctx.connection.push(Cancelable { () =>
            try op.cancel(false)
            catch { case NonFatal(ex) => s.reportFailure(ex) }
          })

          addListener(op, () => {
            // Resetting the frameIndex because we've had an async boundary
            ctx.frameRef.reset()
            // Need to pop the current cancelable, as a matter of contract
            ctx.connection.pop()
            // Go, go, go
            invoke(op, cb, map)
          })
        }
      } catch {
        case NonFatal(ex) =>
          if (streamErrors) cb.asyncOnError(ex)(ctx.scheduler)
          else ctx.scheduler.reportFailure(ex)
      }
    }
  }

  @inline
  private def withPrefix(key: String): String =
    if (prefix.isEmpty)
      key
    else
      prefix + "-" + key

  private[this] val prefix =
    config.keysPrefix.getOrElse("")

  private[this] val client = {
    if (System.getProperty("net.spy.log.LoggerImpl") == null) {
      System.setProperty(
        "net.spy.log.LoggerImpl",
        "shade.memcached.internals.Slf4jLogger"
      )
    }

    val conn = {
      val builder = new ConnectionFactoryBuilder()
        .setProtocol(
          if (config.protocol == Protocol.Binary)
            SpyProtocol.BINARY
          else
            SpyProtocol.TEXT
        )
        .setDaemon(true)
        .setFailureMode(config.failureMode match {
          case FailureMode.Retry =>
            SpyFailureMode.Retry
          case FailureMode.Cancel =>
            SpyFailureMode.Cancel
          case FailureMode.Redistribute =>
            SpyFailureMode.Redistribute
        })
        .setOpQueueFactory(config.opQueueFactory.orNull)
        .setReadOpQueueFactory(config.readQueueFactory.orNull)
        .setWriteOpQueueFactory(config.writeQueueFactory.orNull)
        .setShouldOptimize(config.shouldOptimize)
        .setHashAlg(config.hashAlgorithm)
        .setLocatorType(config.locator)

      val withTimeout = config.operationTimeout match {
        case _: FiniteDuration =>
          builder.setOpTimeout(config.operationTimeout.toMillis)
        case _ =>
          builder
      }

      val withAuth = config.authentication match {
        case Some(credentials) =>
          withTimeout.setAuthDescriptor(
            new AuthDescriptor(
              Array("PLAIN"),
              new PlainCallbackHandler(credentials.username, credentials.password)
            )
          )
        case None =>
          withTimeout
      }

      withAuth
    }

    import scala.collection.JavaConverters._
    val addresses = AddrUtil.getAddresses(config.addresses).asScala
    new MemcachedClient(conn.build(), addresses.asJava)
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

  override def close(): Unit = {
    client.shutdown()
  }
}
