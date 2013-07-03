package shade.concurrency.atomic

import java.util.concurrent.atomic.AtomicInteger

/**
 * A [[shade.concurrency.atomic.Ref]] with an
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicInteger.html AtomicInteger]]
 * as the underlying atomic reference.
 *
 *@see The documentation on [[shade.concurrency.atomic.Ref]]
 */
final class RefInt(initialValue: Int) extends Ref[Int] {
  def set(update: Int) {
    instance.set(update)
  }

  def get: Int =
    instance.get()

  def getAndSet(update: Int): Int = {
    instance.getAndSet(update)
  }

  def compareAndSet(expect: Int, update: Int): Boolean = {
    instance.compareAndSet(expect, update)
  }

  override def increment(implicit num: Numeric[Int]) {
    instance.incrementAndGet()
  }

  override def incrementAndGet(implicit num: Numeric[Int]): Int =
    instance.incrementAndGet()

  override def getAndIncrement(implicit num: Numeric[Int]): Int =
    instance.getAndIncrement

  override def decrement(implicit num: Numeric[Int]) {
    instance.decrementAndGet()
  }

  override def decrementAndGet(implicit num: Numeric[Int]): Int =
    instance.decrementAndGet()

  override def getAndDecrement(implicit num: Numeric[Int]): Int =
    instance.getAndDecrement


  private[this] val instance = new AtomicInteger(initialValue)
}
