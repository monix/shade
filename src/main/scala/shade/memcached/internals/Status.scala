package shade.memcached.internals

sealed trait Status
case object TimedOutStatus extends Status
case object CancelledStatus extends Status
case object CASExistsStatus extends Status
case object CASNotFoundStatus extends Status
case object CASSuccessStatus extends Status
case object CASObserveErrorInArgs extends Status
case object CASObserveModified extends Status
case object CASObserveTimeout extends Status
case object IllegalCompleteStatus extends Status
