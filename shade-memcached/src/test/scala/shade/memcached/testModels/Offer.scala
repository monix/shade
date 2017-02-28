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

import java.util.UUID

@SerialVersionUID(389230490582490966L)
final case class Offer(
  id: Option[Int],
  name: String,

  advertiser: Advertiser,
  offerType: String,

  liveDeal: LiveDealInfo,
  creative: OfferCreative,

  deliveryMechanisms: Seq[String],

  servedURL: String,
  realURL: Option[String],

  // is_active and is_valid
  isRunning: Boolean,
  isDynamic: Boolean,
  isGlobal: Boolean,

  countries: Seq[String]) {

  def uniqueToken = {
    val token = id.toString + "-" + advertiser.serviceID +
      "-" + liveDeal.uid.getOrElse("static")
    UUID.nameUUIDFromBytes(token.getBytes).toString
  }

  def isExpired = {
    if (liveDeal.expires.isEmpty)
      false
    else if (liveDeal.expires.get > System.currentTimeMillis() / 1000)
      false
    else
      true
  }
}

@SerialVersionUID(389230490582490944L)
final case class LiveDealInfo(
  uid: Option[String],
  expires: Option[Int],
  refreshToken: Option[Int],
  searchKeyword: Option[String])

@SerialVersionUID(389230490582490955L)
final case class OfferCreative(
  title: String,
  description: String,
  merchantName: Option[String],
  merchantPhone: Option[String],
  htmlDescription: Option[String])
