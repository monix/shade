package shade.testModels

import java.util.UUID

case class Offer(
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

case class LiveDealInfo(
  uid: Option[String],
  expires: Option[Int],
  refreshToken: Option[Int],
  searchKeyword: Option[String])

case class OfferCreative(
  title: String,
  description: String,
  merchantName: Option[String],
  merchantPhone: Option[String],
  htmlDescription: Option[String])
