package shade.concurrency.atomic

import scala.annotation.tailrec

/**
 * A `Ref` is the common trait of all reference types.
 *
 * Initializing a `Ref` is done through the corresponding `Ref.apply` method,
 * of its companion object and depending on the given parameter it returns
 * one of its sub-classes.
 *
 * To get a [[shade.concurrency.atomic.RefInt]]: {{{
 *
 *   val value: RefInt = Ref(123)
 *
 * }}}
 *
 * To get a [[shade.concurrency.atomic.RefLong]]: {{{
 *
 *   val value: RefLong = Ref(10293840239L)
 *
 * }}}
 *
 * To get a [[shade.concurrency.atomic.RefBoolean]]: {{{
 *
 *   val value: RefBoolean = Ref(true)
 *
 * }}}
 *
 * To get a [[shade.concurrency.atomic.RefAny]]: {{{
 *
 *   val value: RefAny = Ref(BigInt(1))
 *
 * }}}
 *
 * In the above examples the type annotations are only given as
 * clarification for what `Ref.apply` returns, as otherwise those type
 * annotations are not needed, or can be of type `Ref[T]`.
 *
 * Example for generating a unique per-process ID, based on `compareAndSet`: {{{
 *
 *    // our counter, holding the last generated value
 *   val lastID = Ref(0L)
 *
 *   @tailrec
 *   def generateID = {
 *     val currentValue = lastID.get
 *     val updatedValue = currentValue + 1
 *
 *     if (lastID.compareAndSet(currentValue, updatedValue))
 *       updatedValue
 *     else
 *       generateID
 *   }
 * }}}
 *
 * Instead of manually using `compareAndSet` in this case, we might
 * as well use higher-level utilities, like `transformAndGet`: {{{
 *
 *   // our counter, holding the last generated value
 *   val lastID = Ref(0L)
 *
 *   def generateID = lastID.transformAndGet(x => x + 1)
 *
 * }}}
 *
 * The above code will generate IDs starting with 1, however if we
 * want to start with `0`, we could use `getAndTransform` which returns
 * the value that was updated: {{{
 *
 *   // our counter, holding the next ID to be returned
 *   val nextID = Ref(0L)
 *
 *   def generateID = nextID.getAndTransform(x => x + 1)
 *
 * }}}
 *
 * Incrementing stuff is such a common scenario that `Ref` also provides a
 * helper for all `T` types that implement the
 * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]] type-class.
 * In case of `RefInt` and `RefLong` this operation also fallbacks to the
 * implementations provided by
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicInteger.html#getAndIncrement() AtomicInteger]]
 * and
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicLong.html#getAndIncrement() AtomicLong]]
 * for performance reasons.
 *
 * {{{
 *   // our counter, holding the last generated value
 *   val lastID = Ref(0L)
 *
 *   def generateID = lastID.incrementAndGet
 * }}}
 *
 * The beauty of `Ref` is that this works for ''any number'', in comparison with the
 * classes provided in the Java library:
 *
 * {{{
 *   // our counter is not a BigInt
 *   val lastID = Ref(BigInt(0))
 *
 *   def generateID: BigInt = lastID.incrementAndGet
 * }}}
 *
 * Works for doubles too:
 *
 * {{{
 *   // our counter is not a Double
 *   val lastID = Ref(0.0)
 *
 *   def generateID: Double = lastID.incrementAndGet
 * }}}
 *
 * @tparam T The type parameter of the enclosing value being held by this reference.
 *           It is being specialized by the compiler for `Int`, `Long` and `Boolean`,
 *           for performance reasons.
 */
trait Ref[@specialized(scala.Int, scala.Long, scala.Boolean) T] {
  /**
   * Gets the current value.
   */
  def get: T

  /**
   * Updates the current value.
   */
  def set(update: T)

  /**
   * Atomically sets the value to the given updated value if
   * the current value == the expected value.
   *
   * @param expect - the expected value
   * @param update - the new value for update
   * @return - either `true`, in case the update succeeded (the expected value was there) or `false` otherwise
   */
  def compareAndSet(expect: T, update: T): Boolean

  /**
   * Atomically sets to the given value and returns the old value.
   * @param update - the new value
   * @return - the previous value
   */
  def getAndSet(update: T): T

  /**
   * Alias of `get`
   */
  final def apply(): T = get

  /**
   * Increments the current value, if the generic
   * type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.transform(_ + 1)
   * }}}
   */
  def increment(implicit num : Numeric[T]) {
    transform(x => num.plus(x, num.one))
  }

  /**
   * Decrements the current value, if the generic
   * type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.transform(_ - 1)
   * }}}
   */
  def decrement(implicit num : Numeric[T]) {
    transform(x => num.minus(x, num.one))
  }

  /**
   * Increments the current value, returning the new value,
   * if the generic type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.transformAndGet(_ + 1)
   * }}}
   */
  def incrementAndGet(implicit num : Numeric[T]) =
    transformAndGet(x => num.plus(x, num.one))

  /**
   * Decrements the current value, returning the new value,
   * if the generic type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.transformAndGet(_ - 1)
   * }}}
   */
  def decrementAndGet(implicit num : Numeric[T]) =
    transformAndGet(x => num.minus(x, num.one))

  /**
   * Increments the current value, returning the old value,
   * if the generic type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.getAndTransform(_ + 1)
   * }}}
   */
  def getAndIncrement(implicit num : Numeric[T]) =
    getAndTransform(x => num.plus(x, num.one))

  /**
   * Decrements the current value, returning the old value,
   * if the generic type `T` belongs in the
   * [[http://www.scala-lang.org/api/current/index.html#scala.math.Numeric math.Numeric]]
   * type-class, or if an implementation for `Numeric[T]` is provided.
   *
   * It is a shortcut for: {{{
   *   ref.getAndTransform(_ - 1)
   * }}}
   */
  def getAndDecrement(implicit num : Numeric[T]) =
    getAndTransform(x => num.minus(x, num.one))

  /**
   * For transforming the current value, while specifying
   * exactly the value to be returned by the function (extracted).
   *
   * @param cb - the transformation, taking as parameter the current value and returning
   *             a tuple with the updated value + the value to extract
   * @tparam U - the type of the value to extract
   * @return - the extracted value
   *
   * @example {{{
   *   val ref = Ref(List.empty[String])
   *
   *   // pushes an element on the stack,
   *   // returning the new length of the stack
   *   def pushElem(e: String): Int =
   *     ref.transformAndExtract { list =>
   *       val newList = e :: list
   *       (newList, newList.length)
   *     }
   * }}}
   */
  @tailrec
  final def transformAndExtract[U](cb: T => (T, U)): U = {
    val value = get
    val (newValue, extract) = cb(value)

    if (!compareAndSet(value, newValue))
      transformAndExtract(cb)
    else
      extract
  }

  /**
   * Transforms the current value, with the provided function,
   * returning the updated value.
   *
   * @param cb - the transformation
   * @return - the updated value
   *
   * @example {{{
   *
   *   val value = Ref(List.empty[Int])
   *
   *   def pushElement(e: Int): List[Int] =
   *     value.transformAndGet {
   *       case head::tail =>
   *         e :: head :: tail
   *       case Nil =>
   *         e :: Nil
   *     }
   *
   * }}}
   */
  @tailrec
  final def transformAndGet(cb: T => T): T = {
    val oldValue = get
    val newValue = cb(oldValue)

    if (!compareAndSet(oldValue, newValue))
      transformAndGet(cb)
    else
      newValue
  }

  /**
   * Transforms the current value, using the provided function,
   * returning the old value that was updated.
   *
   * @param cb - the transformation
   * @return - the old value, before the transformation took place
   *
   * @example {{{
   *   val value = Ref(Map.empty[String, String])
   *
   *   // updates map, returning true in case the key
   *   // wasn't already there, or false otherwise
   *   def updateMap(key: String, value: String): Boolean = {
   *     val oldMap = value.getAndTransform(map => map.updated(key, value))
   *     oldMap.contains(key)
   *   }
   * }}}
   */
  @tailrec
  final def getAndTransform(cb: T => T): T = {
    val oldValue = get
    val update = cb(oldValue)

    if (!compareAndSet(oldValue, update))
      getAndTransform(cb)
    else
      oldValue
  }

  /**
   * Transforms the current value, with the provided function.
   *
   * @param cb - the transformation
   * @return - either `true` if the updated value is different
   *         from the current value, or `false` otherwise.
   *
   * @example {{{
   *
   *   val value = Ref(List.empty[Int])
   *
   *   def pushElement(e: Int) {
   *     value.transform {
   *       case head::tail =>
   *         e :: head :: tail
   *       case Nil =>
   *         e :: Nil
   *     }
   *   }
   *
   * }}}
   */
  @tailrec
  final def transform(cb: T => T): Boolean = {
    val value = get
    val update = cb(value)

    if (!compareAndSet(value, update))
      transform(cb)
    else
      value != update
  }

  /**
   * Returns the String representation of the current value.
   */
  override def toString: String =
    "Ref(" + get.toString + ")"
}

/**
 * Provides constructors for [[shade.concurrency.atomic.Ref]]
 */
object Ref {
  def apply(initialValue: Int): RefInt =
    new RefInt(initialValue)

  def apply(initialValue: Long): RefLong =
    new RefLong(initialValue)

  def apply(initialValue: Boolean): RefBoolean =
    new RefBoolean(initialValue)

  def apply[T](initialValue: T): Ref[T] =
    new RefAny[T](initialValue)
}
