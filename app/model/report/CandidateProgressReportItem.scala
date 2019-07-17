/*
 * Copyright 2019 HM Revenue & Customs
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

package model.report

import model.ApplicationRoute.ApplicationRoute
import model.SchemeId
import play.api.libs.json.Json

case class CandidateProgressReportItem(userId: String, applicationId: String, progress: Option[String], schemes: List[SchemeId],
                                       disability: Option[String], onlineAdjustments: Option[String],
                                       assessmentCentreAdjustments: Option[String], phoneAdjustments: Option[String],
                                       gis: Option[String], civilServant: Option[String], fastTrack: Option[String], edip: Option[String],
                                       sdipPrevious: Option[String], sdip: Option[String],
                                       fastPassCertificate: Option[String], assessmentCentre: Option[String], applicationRoute: ApplicationRoute)

object CandidateProgressReportItem {
  implicit val candidateProgressReportFormat = Json.format[CandidateProgressReportItem]
}
