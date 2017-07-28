package model.persisted.sift

import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class SchemeSpecificAnswer(rawText: String)

object SchemeSpecificAnswer
{
  implicit val schemeSpecificAnswerFormat = Json.format[SchemeSpecificAnswer]
  implicit val schemeAnswersHandler = Macros.handler[SchemeSpecificAnswer]
}
