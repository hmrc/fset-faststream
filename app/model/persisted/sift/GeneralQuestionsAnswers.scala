package model.persisted.sift

import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class GeneralQuestionsAnswers(
  multiplePassports: Boolean,
  passportCountry: String,
  hasUndergradDegree: Boolean,
  hasPostgradDegree: Boolean
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]
  implicit val generalQuestionsAnswersHandler = Macros.handler[GeneralQuestionsAnswers]
}