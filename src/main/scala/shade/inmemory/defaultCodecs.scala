package shade.inmemory

import shade.CacheCodec

object defaultCodecs {
  private[this] val inMemoryHelper = new CacheCodec[Any,Any] {
    def serialize(value: Any): Any = value
    def deserialize(data: Any): Any = data
  }

  implicit def InMemoryAnyCodec[S]: CacheCodec[S, Any] =
    inMemoryHelper.asInstanceOf[CacheCodec[S, Any]]
}

