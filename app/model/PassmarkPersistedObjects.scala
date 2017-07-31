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

package model

import org.joda.time.DateTime
import play.api.libs.json.Json

object PassmarkPersistedObjects {

  case class AssessmentCentrePassMarkSettings(
    schemes: List[AssessmentCentrePassMarkScheme],
    info: AssessmentCentrePassMarkInfo
  )

  case class AssessmentCentrePassMarkInfo(version: String, createDate: DateTime, createdByUser: String)

  // TODO: Ian Miguel should change to SchemeId
  case class AssessmentCentrePassMarkScheme(schemeName: String, overallPassMarks: Option[PassMarkSchemeThreshold] = None)

  case class PassMarkSchemeThreshold(failThreshold: Double, passThreshold: Double)

  object Implicits {
    implicit val PersistedPassMarkSchemeThresholdFormat = Json.format[PassMarkSchemeThreshold]
    implicit val AssessmentCentrePassMarkInfoFormat = Json.format[AssessmentCentrePassMarkInfo]
    implicit val PersistedAssessmentCentrePassMarkSchemeFormat = Json.format[AssessmentCentrePassMarkScheme]
    implicit val PersistedAssessmentCentrePassMarkSettingsFormat = Json.format[AssessmentCentrePassMarkSettings]
  }

}
