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

@SerialVersionUID(389230490582490933L)
final case class Impression(
  uuid: String,
  session: Session,
  servedOffers: List[Offer] = Nil,
  requestCount: Int = 0,
  alreadyServed: Boolean = false,
  clientVersion: Option[String] = None)