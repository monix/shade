package shade.testModels

case class Session(
  uuid: String,
  deviceID: String,
  device: String,
  userInfo: UserInfo,
  appID: Option[String] = None,
  servedBy: Option[String] = None,
  userIP: Option[String] = None,
  locationLat: Option[Float] = None,
  locationLon: Option[Float] = None,
  countryCode: Option[String] = None
)

