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

package model.report

import model.Commands.{PhoneAndEmail, ReportWithPersonalDetails}
import model.OnlineTestCommands.TestResult
import play.api.libs.json.Json
import model.Commands.Implicits._
import model.OnlineTestCommands.Implicits._
import model.SchemeType._
import model.report.QuestionnaireReportItem

case class OnlineTestPassMarkReportItem(
                           application: ApplicationForOnlineTestPassMarkReportItem,
                           questionnaire: QuestionnaireReportItem)

case class ApplicationForOnlineTestPassMarkReportItem(applicationId: String,
                                                      schemes: List[SchemeType],
                                                      disability: Option[String],
                                                      gis: Option[Boolean],
                                                      onlineAdjustments: Option[String],
                                                      assessmentCentreAdjustments: Option[String],
                                                      testResults: PassMarkReportTestResults)

case class PassMarkReportTestResults(
                                      behavioural: Option[TestResult],
                                      situational: Option[TestResult]
                                    )

case class PassMarkReportWithPersonalData(application: ReportWithPersonalDetails,
                                          testResults: PassMarkReportTestResults,
                                          contactDetails: PhoneAndEmail)






object PassMarkReportTestResults {
  implicit val passMarkReportTestDataFormat = Json.format[PassMarkReportTestResults]
}

object PassMarkReportWithPersonalData {
  implicit val passMarkReportWithPersonalDetailsFormat = Json.format[PassMarkReportWithPersonalData]
}


object ApplicationForOnlineTestPassMarkReportItem {
  implicit val applicationForOnlineTestReportItemFormat = Json.format[ApplicationForOnlineTestPassMarkReportItem]
}

object OnlineTestPassMarkReportItem {
  implicit val onlineTestPassMarkReportFormat = Json.format[OnlineTestPassMarkReportItem]
}




