package shade

import scala.collection.mutable.ArrayBuffer

package object testModels {
  val bigInstance = Impression(
    "96298b14-1e13-a162-662b-969bd3b41ca4",
    Session(
      "c5c94985-1d91-3a8b-b36b-6791efefc38c",
      "dummy-user-sa9d08ahusid",
      "android.web",
      UserInfo(
        "71.89.145.102",
        "71.89.145.102",
        "71.89.145.102",
        "Mozilla/5.0 (Linux; U; Android 0.5; en-us) AppleWebKit/522  (KHTML, like Gecko) Safari/419.3",
        Some(
          GeoIPLocation(
            "us",
            Some("Ashburn"),
            Some("United States"),
            Some(39.0437.toFloat),
            Some(-77.4875.toFloat),
            Some(703),
            None,
            Some("VA"),
            Some(511)))),

      Some("aac636be-e42b-01d6-449b-6a0c2e5e7b09"),
      Some("something-65"),
      Some("71.89.145.102"),
      None,
      None,
      Some("us")),
    List(
      Offer(
        Some(3352251),
        "Some Dummy Offer Title",
        Advertiser(
          Some(137),
          Some("something"),
          "something"),
        "cpa",
        LiveDealInfo(
          Some(""),
          None,
          None,
          None),

        OfferCreative(
          "So Many Dresses!",
          "Daily Deals For Moms, Babies and Kids. Up to 90% OFF! Shop Now!",
          Some("Something.com"),
          Some(""),
          None),

        ArrayBuffer("viewnow"),

        "http://something.com/track?clickID=242323&pubID=982345&something=219&subID=something",
        None,
        true,
        false,
        false,
        List("us"))),
    112,
    true,
    Some("light-fullscreen"))

  val bigInstance2 = Impression(
    "96298b14-1e13-a162-662b-969bd3b41ca4",
    Session(
      "c5c94985-1d91-3a8b-b36b-6791efefc38c",
      "dummy-user-sa9d08ahusid",
      "android.web",
      UserInfo(
        "71.89.145.102",
        "71.89.145.102",
        "71.89.145.102",
        "Mozilla/5.0 (Linux; U; Android 0.5; en-us) AppleWebKit/522  (KHTML, like Gecko) Safari/419.3",
        Some(
          GeoIPLocation(
            "us",
            Some("Ashburn"),
            Some("United States"),
            Some(39.0437.toFloat),
            Some(-77.4875.toFloat),
            Some(703),
            None,
            Some("VA"),
            Some(511)))),

      Some("aac636be-e42b-01d6-449b-6a0c2e5e7b09"),
      Some("something-65"),
      Some("71.89.145.102"),
      None,
      None,
      Some("us")),
    List.empty,
    112,
    true,
    Some("light-fullscreen"))
}
