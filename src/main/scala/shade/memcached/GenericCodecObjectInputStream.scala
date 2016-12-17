/*
 * Copyright (c) 2012-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://github.com/monix/shade
 *
 * Licensed under the MIT License (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 * https://github.com/monix/shade/blob/master/LICENSE.txt
 */

package shade.memcached

import java.io.{ InputStream, ObjectInputStream, ObjectStreamClass }

import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * Object input stream which tries the thread local class loader.
 *
 * Thread Local class loader is used by SBT to avoid polluting system class loader when
 * running different tasks.
 *
 * This allows deserialization of classes from sub-projects during something like
 * Play's test/run modes.
 */
class GenericCodecObjectInputStream(classTag: ClassTag[_], in: InputStream)
    extends ObjectInputStream(in) {

  private def classTagClassLoader =
    classTag.runtimeClass.getClassLoader
  private def threadLocalClassLoader =
    Thread.currentThread().getContextClassLoader

  override protected def resolveClass(desc: ObjectStreamClass): Class[_] = {
    try classTagClassLoader.loadClass(desc.getName) catch {
      case NonFatal(_) =>
        try super.resolveClass(desc) catch {
          case NonFatal(_) =>
            threadLocalClassLoader.loadClass(desc.getName)
        }
    }
  }
}
