/*
 * Copyright 2022 HM Revenue & Customs
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
import org.joda.time.DateTime
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import repositories._
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class AssessmentScoresFinalFeedback(
  feedback: String,
  updatedBy: UniqueIdentifier,
  acceptedDate: DateTime,
  version: Option[String] = None
) extends AssessmentScoresSection

object AssessmentScoresFinalFeedback {
  implicit val jsonFormat = Json.format[AssessmentScoresFinalFeedback]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentScoresFinalFeedback] =
    Macros.handler[AssessmentScoresFinalFeedback]
}
