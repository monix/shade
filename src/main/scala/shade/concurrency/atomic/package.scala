package shade.concurrency

/**
 * Wrappers around the atomic references provided by the standard
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic.html java.util.concurrent.atomic]]
 * package, provided for ease of use and extra utilities.
 *
 * Atomic references are classes that support lock-free thread-safe programming
 * on single variables. They are much like simple
 * [[http://www.scala-lang.org/api/current/index.html#scala.volatile volatile]]
 * references, with the addition of an atomic conditional update: {{{
 *
 *   def compareAndSet(expect: T, update: T): Boolean
 *
 * }}}
 *
 * On modern processors, this instruction is optimized and has a non-blocking behavior,
 * however depending on the underlying architecture it does not guarantee that the
 * behavior is non-blocking, although in general it is.
 *
 * The Java standard library provides amongst others
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicReference.html AtomicReference]],
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicInteger.html AtomicInteger]],
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicLong.html AtomicLong]] and
 * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/concurrent/atomic/AtomicBoolean.html AtomicBoolean]].
 * The supported operations vary and [[shade.concurrency.atomic.Ref]]
 * is a unified interface of them all, with higher-level helpers to aid in the
 * development of non-blocking algorithms.
 *
 * @example {{{
 * import shade.concurrent.atomic.Ref
 *
 * val intValue = Ref(0)
 *
 * def increment = intValue.incrementAndGet
 *
 * def multiplyBy(n: Int) = value.transformAndGet(_ * value)
 * }}}
 *
 * @see The documentation on [[shade.concurrency.atomic.Ref]]
 */
package object atomic

