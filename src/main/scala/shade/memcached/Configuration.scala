package shade.memcached

import concurrent.duration._

case class Configuration(
  /**
   * List of server addresses, separated by space, e.g.
   *
   *    192.168.1.3:11211 192.168.1.4:11211
   */
  addresses: String,

  /**
   * Authentication credentials (if None, then no
   * authentication is performed)
   */
  authentication: Option[AuthConfiguration] = None,

  /**
   * Prefix to be added to used keys when storing/retrieving values,
   * useful for having the same Memcached instances used by several
   * applications to prevent them from stepping over each other.
   */
  keysPrefix: Option[String] = None,

  /**
   * Can be either Text or Binary.
   */
  protocol: Protocol.Value = Protocol.Binary,

  /**
   * Specifies failure mode for SpyMemcached when connections drop.
   *
   * - in Retry mode a connection is retried until it recovers.
   * - in Cancel mode all operations are cancelled
   * - in Redistribute mode, the client tries to redistribute operations to other nodes
   */
  failureMode: FailureMode.Value = FailureMode.Retry,

  /**
   * Default operation timeout.
   *
   * When the limit is reached, the Future responses finish
   * with Failure(TimeoutException)
   */
  operationTimeout: FiniteDuration = 1.second
)


object Protocol extends Enumeration {
  type Type = Value
  val Binary, Text = Value
}

object FailureMode extends Enumeration {
  val Retry, Cancel, Redistribute = Value
}

case class AuthConfiguration(
  username: String,
  password: String
)