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
import model.persisted.Media

case class ApplicationForDiversityReportItem(applicationId: String,
                                             userId: String,
                                             progress: Option[String],
                                             schemes: List[SchemeType],
                                             disability: Option[String],
                                             gis: Option[Boolean],
                                             onlineAdjustments: Option[String],
                                             assessmentCentreAdjustments: Option[String],
                                             civilServiceExperiencesDetails: Option[CivilServiceExperienceDetailsReportItem]
                                            )

case class DiversityReportItem(
                           application: ApplicationForDiversityReportItem,
                           questionnaire: Option[QuestionnaireReportItem],
                           media: Option[Media])

object ApplicationForDiversityReportItem {
  implicit val applicationForDiversityReportItemFormat = Json.format[ApplicationForDiversityReportItem]
}

object DiversityReportItem {
  implicit val diversityReportFormat = Json.format[DiversityReportItem]
}
