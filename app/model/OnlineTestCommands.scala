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

package model

import model.PersistedObjects.CandidateTestReport
import model.exchange.passmarksettings.Phase1PassMarkSettings
import play.api.libs.json.Json

object OnlineTestCommands {

  case class OnlineTestApplication(applicationId: String,
                                   applicationStatus: String,
                                   userId: String,
                                   guaranteedInterview: Boolean,
                                   needsOnlineAdjustments: Boolean,
                                   needsAtVenueAdjustments: Boolean,
                                   preferredName: String,
                                   lastName: String,
                                   eTrayAdjustments: Option[AdjustmentDetail],
                                   videoInterviewAdjustments: Option[AdjustmentDetail]) {
    def isInvigilatedETray = eTrayAdjustments.exists(_.invigilatedInfo.isDefined)
    def isInvigilatedVideo = videoInterviewAdjustments.exists(_.invigilatedInfo.isDefined)
  }

  case class OnlineTestApplicationWithCubiksUser(applicationId: String, userId: String, cubiksUserId: Int)

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
    implicit val OnlineTestApplicationUserFormats = Json.format[OnlineTestApplicationWithCubiksUser]
    implicit val OnlineTestReportIdMRAFormats = Json.format[OnlineTestReportAvailability]
    implicit val testFormat = Json.format[TestResult]
  }

}
