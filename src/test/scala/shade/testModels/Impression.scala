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

package shade.testModels

case class Impression(
  uuid: String,
  session: Session,
  servedOffers: Seq[Offer] = Seq.empty,
  requestCount: Int = 0,
  alreadyServed: Boolean = false,
  clientVersion: Option[String] = None)