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
import connectors.PassMarkExchangeObjects.Settings
import model.PersistedObjects.CandidateTestReport
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{BSONDocument, BSONHandler, Macros}

object OnlineTestCommands {
  case class Phase1Test(scheduleId: Int,
    usedForResults: Boolean,
    cubiksUserId: Int,
    testProvider: String = "cubiks",
    token: String,
    testUrl: String,
    invitationDate: DateTime,
    participantScheduleId: Int,
    startedDateTime: Option[DateTime] = None,
    completedDateTime: Option[DateTime] = None,
    resultsReadyToDownload: Boolean = false,
    reportId: Option[Int] = None,
    reportLinkURL: Option[String] = None,
    reportStatus: Option[String] = None,
    testResult: Option[model.persisted.TestResult] = None
  )

  object Phase1Test {
    import repositories.BSONDateTimeHandler
    implicit val phase1TestHandler: BSONHandler[BSONDocument, Phase1Test] = Macros.handler[Phase1Test]
    implicit val phase1TestFormat = Json.format[Phase1Test]
  }

  case class Phase1TestProfile(
                                expirationDate: DateTime,
                                tests: List[Phase1Test]
  ) {
    def activeTests = tests filter (_.usedForResults)

    def hasNotStartedYet = activeTests.forall(_.startedDateTime.isEmpty)

    def hasNotCompletedYet =  activeTests.exists(_.completedDateTime.isEmpty)

    def hasNotResultReadyToDownloadForAllTestsYet =  activeTests.exists(!_.resultsReadyToDownload)
  }

  object Phase1TestProfile {
    import repositories.BSONDateTimeHandler
    implicit val phase1TestProfileHandler: BSONHandler[BSONDocument, Phase1TestProfile] = Macros.handler[Phase1TestProfile]
    implicit val phase1TestProfileFormat = Json.format[Phase1TestProfile]
  }

  case class OnlineTestApplication(applicationId: String, applicationStatus: String, userId: String,
    guaranteedInterview: Boolean, needsAdjustments: Boolean, preferredName: String,
    timeAdjustments: Option[TimeAdjustmentsOnlineTestApplication])

  case class OnlineTestApplicationWithCubiksUser(applicationId: String, userId: String, cubiksUserId: Int)
  case class OnlineTestApplicationForReportRetrieving(userId: Int, locale: String, reportId: Int, norms: List[ReportNorm])
  case class OnlineTestReportAvailability(reportId: Int, available: Boolean)
  case class OnlineTestReport(xml: Option[String])

  case class CandidateScoresWithPreferencesAndPassmarkSettings(
    passmarkSettings: Settings, // pass and fail mark
    preferences: Preferences, // preferences which scheme candidates like
    scores: CandidateTestReport, // applicationId + scores
    applicationStatus: String
  )

  case class TimeAdjustmentsOnlineTestApplication(verbalTimeAdjustmentPercentage: Int, numericalTimeAdjustmentPercentage: Int)
  case class TestResult(status: String, norm: String,
    tScore: Option[Double], percentile: Option[Double], raw: Option[Double], sten: Option[Double])

  object Implicits {
    implicit val TimeAdjustmentsOnlineTestApplicationFormats = Json.format[TimeAdjustmentsOnlineTestApplication]
    implicit val ApplicationForOnlineTestingFormats = Json.format[OnlineTestApplication]
    implicit val OnlineTestReportNormFormats = Json.format[ReportNorm]
    implicit val OnlineTestApplicationForReportRetrievingFormats = Json.format[OnlineTestApplicationForReportRetrieving]
    implicit val OnlineTestApplicationUserFormats = Json.format[OnlineTestApplicationWithCubiksUser]
    implicit val OnlineTestReportIdMRAFormats = Json.format[OnlineTestReportAvailability]
    implicit val testFormat = Json.format[TestResult]
  }
}
