package shade.memcached.internals

import net.spy.memcached.ops.OperationStatus
import scala.language.existentials

sealed trait Status extends Product with Serializable
case object TimedOutStatus extends Status
case object CancelledStatus extends Status
case object CASExistsStatus extends Status
case object CASNotFoundStatus extends Status
case object CASSuccessStatus extends Status
case object CASObserveErrorInArgs extends Status
case object CASObserveModified extends Status
case object CASObserveTimeout extends Status
case object IllegalCompleteStatus extends Status

object UnhandledStatus {

  /**
   * Builds a serialisable UnhandledStatus from a given [[OperationStatus]] from SpyMemcached
   */
  def fromSpyMemcachedStatus(spyStatus: OperationStatus): UnhandledStatus = UnhandledStatus(spyStatus.getClass, spyStatus.getMessage)
}

final case class UnhandledStatus(statusClass: Class[_], message: String) extends Status