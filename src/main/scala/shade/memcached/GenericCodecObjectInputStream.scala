package shade.memcached

import java.io.{ObjectStreamClass, InputStream, ObjectInputStream}

import scala.reflect.ClassTag
import scala.util.Try

/**
 * Object input stream which tries the thread local class loader.
 *
 * Thread Local class loader is used by SBT to avoid polluting system class loader when
 * running different tasks.
 *
 * This allows deserialisation of classes from subprojects during something like
 * Play's test/run modes.
 */
class GenericCodecObjectInputStream(classTag: ClassTag[_], in: InputStream) extends ObjectInputStream(in) {

  private def classTagClassLoader = classTag.runtimeClass.getClassLoader
  private def threadLocalClassLoader = Thread.currentThread().getContextClassLoader

  override protected def resolveClass(desc: ObjectStreamClass): Class[_] = {
    Try(classTagClassLoader.loadClass(desc.getName)).
      orElse(Try(super.resolveClass(desc))).
      getOrElse(threadLocalClassLoader.loadClass(desc.getName))
  }
}
