package shade.concurrency.atomic

import java.util.concurrent.atomic.AtomicReference


/**
 * Ref with an
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicReference.html AtomicReference]]
 * as the underlying atomic reference.
 *
 *@see The documentation on [[shade.concurrency.atomic.Ref]]
 */
final class RefAny[T] private[atomic] (initialValue: T) extends Ref[T] {
  def set(update: T) {
    instance.set(update)
  }

  def get: T =
    instance.get()

  def getAndSet(update: T): T = {
    instance.getAndSet(update)
  }

  def compareAndSet(expect: T, update: T): Boolean = {
    instance.compareAndSet(expect, update)
  }

  private[this] val instance =
    new AtomicReference[T](initialValue)
}

