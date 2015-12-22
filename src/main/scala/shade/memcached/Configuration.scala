package shade.memcached

import concurrent.duration._
import net.spy.memcached.ops.OperationQueueFactory

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
 *
 * @param shouldOptimize    If true, optimization will collapse multiple sequential get ops.
 *
 * @param opQueueFactory    can be used to customize the operations queue,
 *                          i.e. the queue of operations waiting to be processed by SpyMemcached.
 *                          If `None`, the default SpyMemcached implementation (a bounded ArrayBlockingQueue) is used.
 *
 * @param readQueueFactory  can be used to customize the read queue,
 *                          i.e. the queue of Memcached responses waiting to be processed by SpyMemcached.
 *                          If `None`, the default SpyMemcached implementation (an unbounded LinkedBlockingQueue) is used.
 *
 * @param writeQueueFactory can be used to customize the write queue,
 *                          i.e. the queue of operations waiting to be sent to Memcached by SpyMemcached.
 *                          If `None`, the default SpyMemcached implementation (an unbounded LinkedBlockingQueue) is used.
 */
case class Configuration(
  addresses: String,
  authentication: Option[AuthConfiguration] = None,
  keysPrefix: Option[String] = None,
  protocol: Protocol.Value = Protocol.Binary,
  failureMode: FailureMode.Value = FailureMode.Retry,
  operationTimeout: FiniteDuration = 1.second,
  shouldOptimize: Boolean = false,
  opQueueFactory: Option[OperationQueueFactory] = None,
  writeQueueFactory: Option[OperationQueueFactory] = None,
  readQueueFactory: Option[OperationQueueFactory] = None)

object Protocol extends Enumeration {
  type Type = Value
  val Binary, Text = Value
}

object FailureMode extends Enumeration {
  val Retry, Cancel, Redistribute = Value
}

case class AuthConfiguration(
  username: String,
  password: String)
