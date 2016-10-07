package model.persisted.phase3tests

import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class Phase3TestGroup(expirationDate: DateTime, test: List[Phase3Test])

object Phase3TestGroup {
  implicit val phase3TestGroupFormat = Json.format[Phase3TestGroup]
  import repositories.BSONDateTimeHandler
  implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Macros.handler[Phase3TestGroup]
}
