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

package model

import connectors.ExchangeObjects.ReportNorm
import model.Commands.AdjustmentDetail
import model.PersistedObjects.CandidateTestReport
import model.exchange.passmarksettings.Phase1PassMarkSettings
import play.api.libs.json.Json
import model.Commands.Implicits.adjustmentDetailFormat
import connectors.ExchangeObjects.Implicits.reportNormFormat
import model.Commands.Implicits._
import connectors.ExchangeObjects.Implicits._

object OnlineTestCommands {

  case class OnlineTestApplication(applicationId: String,
                                   applicationStatus: String,
                                   userId: String,
                                   guaranteedInterview: Boolean,
                                   needsAdjustments: Boolean,
                                   preferredName: String,
                                   lastName: String,
                                   //timeAdjustments: Option[TimeAdjustmentsOnlineTestApplication],
                                   eTrayAdjustments: Option[AdjustmentDetail],
                                   videoInterviewAdjustments: Option[AdjustmentDetail])

  case class OnlineTestApplicationWithCubiksUser(applicationId: String, userId: String, cubiksUserId: Int)

  case class OnlineTestApplicationForReportRetrieving(userId: Int, locale: String, reportId: Int, norms: List[ReportNorm])

  case class OnlineTestReportAvailability(reportId: Int, available: Boolean)

  case class OnlineTestReport(xml: Option[String])

  case class CandidateScoresWithPreferencesAndPassmarkSettings(
                                                                passmarkSettings: Phase1PassMarkSettings, // pass and fail mark
                                                                preferences: Preferences, // preferences which scheme candidates like
                                                                scores: CandidateTestReport, // applicationId + scores
                                                                applicationStatus: String
                                                              )

  case class TimeAdjustmentsOnlineTestApplication(etrayTimeAdjustmentPercentage: Int,
                                                  videoInterviewTimeAdjustmentPercentage: Int)

  case class TestResult(status: String, norm: String,
                        tScore: Option[Double], percentile: Option[Double], raw: Option[Double], sten: Option[Double])

  object Implicits {
    implicit val TimeAdjustmentsOnlineTestApplicationFormats = Json.format[TimeAdjustmentsOnlineTestApplication]
    implicit val ApplicationForOnlineTestingFormats = Json.format[OnlineTestApplication]
    implicit val OnlineTestApplicationForReportRetrievingFormats = Json.format[OnlineTestApplicationForReportRetrieving]
    implicit val OnlineTestApplicationUserFormats = Json.format[OnlineTestApplicationWithCubiksUser]
    implicit val OnlineTestReportIdMRAFormats = Json.format[OnlineTestReportAvailability]
    implicit val testFormat = Json.format[TestResult]
  }

}
