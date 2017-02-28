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

package shade.memcached.testModels

@SerialVersionUID(389230490582490932L)
final case class GeoIPLocation(
  countryCode: String,
  city: Option[String],
  countryName: Option[String],
  latitude: Option[Float],
  longitude: Option[Float],
  areaCode: Option[Int],
  postalCode: Option[String],
  region: Option[String],
  dmaCode: Option[Int])