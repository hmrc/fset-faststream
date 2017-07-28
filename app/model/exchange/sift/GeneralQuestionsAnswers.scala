package model.exchange.sift

import play.api.libs.json.Json

case class GeneralQuestionsAnswers(
  multiplePassports: Boolean,
  passportCountry: String,
  hasUndergradDegree: Boolean,
  hasPostgradDegree: Boolean
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]
}