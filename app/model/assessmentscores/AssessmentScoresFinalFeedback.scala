/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package model.assessmentscores

import model.UniqueIdentifier
import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime

case class AssessmentScoresFinalFeedback(
  feedback: String,
  updatedBy: UniqueIdentifier,
  acceptedDate: OffsetDateTime,
  version: Option[String] = None
) extends AssessmentScoresSection {

  def toExchange = AssessmentScoresFinalFeedbackExchange(
    feedback,
    updatedBy,
    acceptedDate,
    version
  )
}

object AssessmentScoresFinalFeedback {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[AssessmentScoresFinalFeedback] = Json.format[AssessmentScoresFinalFeedback]
}

case class AssessmentScoresFinalFeedbackExchange(
  feedback: String,
  updatedBy: UniqueIdentifier,
  acceptedDate: OffsetDateTime,
  version: Option[String] = None
) extends AssessmentScoresSection {

  def toPersistence = AssessmentScoresFinalFeedback(
    feedback,
    updatedBy,
    acceptedDate,
    version
  )
}

object AssessmentScoresFinalFeedbackExchange {
  implicit val jsonFormat: OFormat[AssessmentScoresFinalFeedbackExchange] = Json.format[AssessmentScoresFinalFeedbackExchange]
}
