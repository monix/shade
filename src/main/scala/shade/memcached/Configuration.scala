package shade.memcached

import concurrent.duration._

/**
 * Represents the Memcached connection configuration.
 *
 * @param addresses         the list of server addresses, separated by space,
 *                          e.g. `"192.168.1.3:11211 192.168.1.4:11211"`
 * @param authentication    the authentication credentials (if None, then no authentication is performed)
 *
 * @param keysPrefix        is the prefix to be added to used keys when storing/retrieving values,
 *                          useful for having the same Memcached instances used by several
 *                          applications to prevent them from stepping over each other.
 *
 * @param protocol          can be either `Text` or `Binary`
 *
 * @param failureMode       specifies failure mode for SpyMemcached when connections drop:
 *                          - in Retry mode a connection is retried until it recovers.
 *                          - in Cancel mode all operations are cancelled
 *                          - in Redistribute mode, the client tries to redistribute operations to other nodes
 *
 * @param operationTimeout  is the default operation timeout; When the limit is reached, the
 *                          Future responses finish with Failure(TimeoutException)
 */
case class Configuration(
  addresses: String,
  authentication: Option[AuthConfiguration] = None,
  keysPrefix: Option[String] = None,
  protocol: Protocol.Value = Protocol.Binary,
  failureMode: FailureMode.Value = FailureMode.Retry,
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