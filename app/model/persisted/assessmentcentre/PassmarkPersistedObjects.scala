/*
 * Copyright 2017 HM Revenue & Customs
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

package model.persisted.assessmentcentre

import model.SchemeId
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }
import repositories._

case class AssessmentCentrePassMarkInfo(version: String, createDate: DateTime, createdByUser: String)

object AssessmentCentrePassMarkInfo {
  implicit val jsonFormat = Json.format[AssessmentCentrePassMarkInfo]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkInfo] = Macros.handler[AssessmentCentrePassMarkInfo]
}

case class PassMarkSchemeThreshold(failThreshold: Double, passThreshold: Double)

object PassMarkSchemeThreshold {
  implicit val jsonFormat = Json.format[PassMarkSchemeThreshold]
  implicit val bsonHandler: BSONHandler[BSONDocument, PassMarkSchemeThreshold] = Macros.handler[PassMarkSchemeThreshold]
}

case class AssessmentCentrePassMarkScheme(schemeId: SchemeId, overallPassMarks: Option[PassMarkSchemeThreshold] = None)

object AssessmentCentrePassMarkScheme {
  implicit val jsonFormat = Json.format[AssessmentCentrePassMarkScheme]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkScheme] = Macros.handler[AssessmentCentrePassMarkScheme]
}

case class AssessmentCentrePassMarkSettings(schemes: List[AssessmentCentrePassMarkScheme], info: AssessmentCentrePassMarkInfo)

object AssessmentCentrePassMarkSettings {
  implicit val jsonFormat = Json.format[AssessmentCentrePassMarkSettings]
  implicit val bsonHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkSettings] = Macros.handler[AssessmentCentrePassMarkSettings]
}



