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

import minitest.SimpleTestSuite
import minitest.laws.Checkers
import org.scalacheck.Arbitrary
import shade.memcached.testModels.{ContentPiece, Impression}

object CodecsSuite extends SimpleTestSuite with Checkers {
  /** Properties-based checking for a codec of type A. */
  private def serDesCheck[A](implicit A: Arbitrary[A], codec: Codec[A]): Unit =
    check1 { value: A =>
      val encoded = codec.encode(value)
      val decoded = codec.decode(encoded)
      decoded == value
    }

  test("Int") {
    serDesCheck[Int]
  }

  test("Long") {
    serDesCheck[Long]
  }

  test("Float") {
    serDesCheck[Float]
  }

  test("Double") {
    serDesCheck[Double]
  }

  test("Byte") {
    serDesCheck[Byte]
  }

  test("Boolean") {
    serDesCheck[Boolean]
  }

  test("Char") {
    serDesCheck[Char]
  }

  test("Short") {
    serDesCheck[Short]
  }

  test("String") {
    serDesCheck[String]
  }

  test("Array[Byte]") {
    serDesCheck[Array[Byte]]
  }

  test("List[String]") {
    serDesCheck[List[String]]
  }

  test("testModels.bigInstance") {
    val value = testModels.bigInstance
    val codec = implicitly[Codec[Impression]]

    val encoded = codec.encode(value)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, value)
  }

  test("testModels.bigInstance2") {
    val value = testModels.bigInstance2
    val codec = implicitly[Codec[Impression]]

    val encoded = codec.encode(value)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, value)
  }

  test("testModels.contentSeq") {
    val value = testModels.contentSeq
    val codec = implicitly[Codec[Vector[ContentPiece]]]

    val encoded = codec.encode(value)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, value)
  }
}