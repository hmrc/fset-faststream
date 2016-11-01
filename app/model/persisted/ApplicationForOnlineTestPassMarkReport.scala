/*
 * Copyright 2016 HM Revenue & Customs
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

package model.persisted

import model.SchemeType.SchemeType
import play.api.libs.json.Json
import model.report.{ApplicationForOnlineTestPassMarkReportItem, TestResultsForOnlineTestPassMarkReportItem, TestResultsForOnlineTestPassMarkReportItem$}

case class ApplicationForOnlineTestPassMarkReport(
                                                       applicationId: String,
                                                       progress: String,
                                                       schemes: List[SchemeType],
                                                       disability: Option[String],
                                                       gis: Option[Boolean],
                                                       onlineAdjustments: Option[String],
                                                       assessmentCentreAdjustments: Option[String],
                                                       testResults: TestResultsForOnlineTestPassMarkReportItem
                                                     )

object ApplicationForOnlineTestPassMarkReport {
  implicit val applicationForOnlineTestReportFormat = Json.format[ApplicationForOnlineTestPassMarkReport]
}
