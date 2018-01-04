package model.assessmentscores

import play.api.libs.json.Json

case class FixUserStuckInScoresAccepted(applicationId: String)

object FixUserStuckInScoresAccepted {
  implicit val fixUserStuckInScoresAcceptedFormat = Json.format[FixUserStuckInScoresAccepted]
}