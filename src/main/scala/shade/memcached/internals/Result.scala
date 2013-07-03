package shade.memcached.internals

sealed trait Result[+T]
case class SuccessfulResult[+T](key: String, result: T) extends Result[T]
case class FailedResult(key: String, state: Status) extends Result[Nothing]
