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

package shade.local

object Platform {
  /** Returns the recommended parallelism factor.
    *
    * On the JVM it returns the number of processors available to the Java
    * virtual machine, being equivalent with:
    * {{{
    *   Runtime.getRuntime.availableProcessors()
    * }}}
    *
    * On top of Javascript this is always going to be equal to `1`.
    */
  final val parallelism: Int = 1
}
