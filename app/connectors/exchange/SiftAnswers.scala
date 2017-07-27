package connectors.exchange

import connectors.exchange.SchemeSpecificAnswer
import play.api.libs.json.Json

case class SiftAnswers(applicationId: String, answers: Map[String, SchemeSpecificAnswer])

object SchemeSpecificAnswer {
  implicit val siftAnswersFormat = Json.format[SiftAnswers]
}
