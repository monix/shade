package shade.concurrency.atomic

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [[shade.concurrency.atomic.Ref]] with an
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicBoolean.html AtomicBoolean]]
 * as the underlying atomic reference.
 *
 *@see The documentation on [[shade.concurrency.atomic.Ref]]
 */
final class RefBoolean private[atomic] (initialValue: Boolean) extends Ref[Boolean] {
  def set(update: Boolean) {
    instance.set(update)
  }

  def get: Boolean =
    instance.get()

  def getAndSet(update: Boolean): Boolean = {
    instance.getAndSet(update)
  }

  def compareAndSet(expect: Boolean, update: Boolean): Boolean = {
    instance.compareAndSet(expect, update)
  }

  private[this] val instance = new AtomicBoolean(initialValue)
}
