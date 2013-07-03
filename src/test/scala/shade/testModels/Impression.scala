package shade.testModels

case class Impression(
  uuid: String,
  session: Session,
  servedOffers: Seq[Offer] = Seq.empty,
  requestCount: Int = 0,
  alreadyServed: Boolean = false,
  clientVersion: Option[String] = None
)