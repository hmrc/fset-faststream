/*
 * Copyright 2018 HM Revenue & Customs
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

import model.Commands.PhoneNumber
import model.OnlineTestCommands.TestResult
import org.joda.time.LocalDate
import model.ApplicationStatus._
import play.api.libs.json._
import reactivemongo.bson.Macros

@deprecated("fasttrack version. Create one case class in one file. All persisted case classes are in model.persisted package", "July 2016")
object PersistedObjects {

  case class PersonalDetailsWithUserId(preferredName: String, userId: String)

  case class ApplicationIdWithUserIdAndStatus(applicationId: String, userId: String, applicationStatus: String)

  case class UserIdAndPhoneNumber(userId: String, phoneNumber: Option[PhoneNumber])

  case class CandidateTestReport(applicationId: String, reportType: String,
    competency: Option[TestResult] = None, numerical: Option[TestResult] = None,
    verbal: Option[TestResult] = None, situational: Option[TestResult] = None) {

    def isValid(gis: Boolean) = {
      val competencyValid = competency.exists(testIsValid(tScore = true))
      val situationalValid = situational.exists(testIsValid(tScore = true, raw = true, percentile = true, sten = true))
      val numericalValid = numerical.exists(testIsValid(tScore = true, raw = true, percentile = true))
      val verbalValid = verbal.exists(testIsValid(tScore = true, raw = true, percentile = true))

      competencyValid && situationalValid && (gis ^ (numericalValid && verbalValid))
    }

    private def testIsValid(tScore: Boolean, raw: Boolean = false, percentile: Boolean = false, sten: Boolean = false)(result: TestResult) = {
      !((tScore && result.tScore.isEmpty) ||
        (raw && result.raw.isEmpty) ||
        (percentile && result.percentile.isEmpty) ||
        (sten && result.sten.isEmpty))
    }
  }

  case class OnlineTestPDFReport(applicationId: String)

  case class AllocatedCandidate(candidateDetails: PersonalDetailsWithUserId, applicationId: String, expireDate: LocalDate)

  case class ApplicationProgressStatus(name: String, value: Boolean)
  case class ApplicationProgressStatuses(
    statuses: Option[List[ApplicationProgressStatus]],
    questionnaireStatuses: Option[List[ApplicationProgressStatus]]
  )
  case class ApplicationUser(applicationId: String, userId: String, frameworkId: String,
    applicationStatus: String, progressStatuses: ApplicationProgressStatuses)

  object Implicits {
    implicit val addressFormats = Json.format[Address]
    implicit val personalDetailsWithUserIdFormats = Json.format[PersonalDetailsWithUserId]
    implicit val testFormats = Json.format[TestResult]
    implicit val candidateTestReportFormats = Json.format[CandidateTestReport]
    implicit val allocatedCandidateFormats = Json.format[AllocatedCandidate]
    implicit val applicationProgressStatusFormats = Json.format[ApplicationProgressStatus]
    implicit val applicationProgressStatusesFormats = Json.format[ApplicationProgressStatuses]
    implicit val applicationUserFormats = Json.format[ApplicationUser]

    implicit val onlineTestPdfReportFormats = Json.format[OnlineTestPDFReport]
  }
}
