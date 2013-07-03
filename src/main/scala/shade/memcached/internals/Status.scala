package shade.memcached.internals

trait Status
case object TimedOutStatus extends Status
case object CancelledStatus extends Status
case object CASExistsStatus extends Status
case object CASNotFoundStatus extends Status
case object CASSuccessStatus extends Status
case object IllegalCompleteStatus extends Status