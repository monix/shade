package shade.memcached

import java.io._
import scala.util.control.NonFatal
import shade.CacheCodec

object defaultCodecs {
  implicit object IntBinaryCodec extends CacheCodec[Int, Array[Byte]] {
    def serialize(value: Int): Array[Byte] =
      Array(
        (value >>> 24).asInstanceOf[Byte],
        (value >>> 16).asInstanceOf[Byte],
        (value >>> 8).asInstanceOf[Byte],
        value.asInstanceOf[Byte]
      )

    def deserialize(data: Array[Byte]): Int =
      (data(0).asInstanceOf[Int] & 255) << 24 |
      (data(1).asInstanceOf[Int] & 255) << 16 |
      (data(2).asInstanceOf[Int] & 255) << 8 |
      data(3).asInstanceOf[Int] & 255
  }

  implicit object LongBinaryCodec extends CacheCodec[Long, Array[Byte]] {
    def serialize(value: Long): Array[Byte] =
      Array(
        (value >>> 56).asInstanceOf[Byte],
        (value >>> 48).asInstanceOf[Byte],
        (value >>> 40).asInstanceOf[Byte],
        (value >>> 32).asInstanceOf[Byte],
        (value >>> 24).asInstanceOf[Byte],
        (value >>> 16).asInstanceOf[Byte],
        (value >>> 8).asInstanceOf[Byte],
        value.asInstanceOf[Byte]
      )

    def deserialize(data: Array[Byte]): Long =
      (data(0).asInstanceOf[Long] & 255) << 56 |
      (data(1).asInstanceOf[Long] & 255) << 48 |
      (data(2).asInstanceOf[Long] & 255) << 40 |
      (data(3).asInstanceOf[Long] & 255) << 32 |
      (data(4).asInstanceOf[Long] & 255) << 24 |
      (data(5).asInstanceOf[Long] & 255) << 16 |
      (data(6).asInstanceOf[Long] & 255) << 8 |
      data(7).asInstanceOf[Long] & 255
  }

  implicit object BooleanBinaryCodec extends CacheCodec[Boolean, Array[Byte]] {
    def serialize(value: Boolean): Array[Byte] =
      Array((if (value) 1 else 0).asInstanceOf[Byte])

    def deserialize(data: Array[Byte]): Boolean =
      data.isDefinedAt(0) && data(0) == 1
  }

  implicit object CharBinaryCodec extends CacheCodec[Char, Array[Byte]] {
    def serialize(value: Char): Array[Byte] = Array(
      (value >>> 8).asInstanceOf[Byte],
      value.asInstanceOf[Byte]
    )

    def deserialize(data: Array[Byte]): Char =
      ((data(0).asInstanceOf[Int] & 255) << 8 |
       data(1).asInstanceOf[Int] & 255)
      .asInstanceOf[Char]
  }

  implicit object ShortBinaryCodec extends CacheCodec[Short, Array[Byte]] {
    def serialize(value: Short): Array[Byte] = Array(
      (value >>> 8).asInstanceOf[Byte],
      value.asInstanceOf[Byte]
    )

    def deserialize(data: Array[Byte]): Short =
      ((data(0).asInstanceOf[Short] & 255) << 8 |
        data(1).asInstanceOf[Short] & 255)
        .asInstanceOf[Short]
  }

  implicit object StringBinaryCodec extends CacheCodec[String, Array[Byte]] {
    def serialize(value: String): Array[Byte] = value.getBytes("UTF-8")
    def deserialize(data: Array[Byte]): String = new String(data, "UTF-8")
  }

  private[this] object genericCodec extends CacheCodec[Serializable, Array[Byte]] {
    val classLoader = Thread.currentThread().getContextClassLoader

    def using[T <: Closeable, R](obj: T)(f: T => R): R =
      try
        f(obj)
      finally
        try obj.close() catch {
          case NonFatal(_) => // does nothing
        }

    def serialize(value: Serializable): Array[Byte] =
      using (new ByteArrayOutputStream()) { buf =>
        using (new ObjectOutputStream(buf)) { out =>
          out.writeObject(value)
          out.close()
          buf.toByteArray
        }
      }

    def deserialize(data: Array[Byte]): Serializable =
      using (new ByteArrayInputStream(data)) { buf =>
        val in = new ObjectInputStream(buf) {
          override def resolveClass(desc: ObjectStreamClass): Class[_] = {
            try
              classLoader.loadClass(desc.getName)
            catch {
              case ex: Exception =>
                // try fallback to super implementation
                super.resolveClass(desc)
            }
          }
        }

        using (in) { inp =>
          inp.readObject().asInstanceOf[Serializable]
        }
      }
  }

  implicit def AnyRefBinaryCodec[S <: Serializable]: CacheCodec[S, Array[Byte]] =
    genericCodec.asInstanceOf[CacheCodec[S, Array[Byte]]]
}
