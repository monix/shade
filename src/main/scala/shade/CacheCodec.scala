package shade

/**
 * Represents a type class that needs to be implemented
 * for serialization/deserialization to work.
 *
 * @see [[shade.inmemory.defaultCodecs]] and [[shade.memcached.defaultCodecs]]
 */
trait CacheCodec[S, D] {
  def serialize(value: S): D
  def deserialize(data: D): S
}
