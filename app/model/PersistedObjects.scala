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

import model.Commands.{PhoneNumber, PostCode}
import model.OnlineTestCommands.TestResult
import org.joda.time.{DateTime, LocalDate}
import model.ApplicationStatus._
import play.api.libs.json._

@deprecated("fasttrack version. Create one case class in one file. All persisted case classes are in model.persisted package")
object PersistedObjects {

  @deprecated("fasttrack version")
  case class PersonalDetails(
    firstName: String,
    lastName: String,
    preferredName: String,
    dateOfBirth: LocalDate,
    aLevel: Boolean,
    stemLevel: Boolean
  )

  case class PersonalDetailsWithUserId(preferredName: String, userId: String)

  case class ContactDetails(
    address: Address,
    postCode: PostCode,
    email: String,
    phone: Option[PhoneNumber]
  )

  case class ContactDetailsWithId(
    userId: String,
    address: Address,
    postCode: Option[PostCode],
    email: String,
    phone: Option[PhoneNumber]
  )

  case class ApplicationIdWithUserIdAndStatus(applicationId: String, userId: String, applicationStatus: String)

  case class PersistedAnswer(answer: Option[String], otherDetails: Option[String], unknown: Option[Boolean])
  case class PersistedQuestion(question: String, answer: PersistedAnswer)

  case class UserIdAndPhoneNumber(userId: String, phoneNumber: Option[PhoneNumber])

  case class DiversityEthnicity(collector: Map[String, Int])
  case class DiversitySocioEconomic(collector: Map[String, Int])
  case class DiversityGender(collector: Map[String, Int])
  case class DiversitySexuality(collector: Map[String, Int])

  case class DiversityReportRow(location: String, region: String, total: Int, haveDisabilities: Int, collector: Map[String, Int])

  case class DiversityReport(timeStamp: DateTime, data: List[DiversityReportRow])

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
    implicit val persistedPersonalDetailsFormats = Json.format[PersonalDetails]
    implicit val addressFormats = Json.format[Address]
    implicit val contactDetailsFormats = Json.format[ContactDetails]
    implicit val contactDetailsIdFormats = Json.format[ContactDetailsWithId]
    implicit val answerFormats = Json.format[PersistedAnswer]
    implicit val questionFormats = Json.format[PersistedQuestion]
    implicit val personalDetailsWithUserIdFormats = Json.format[PersonalDetailsWithUserId]

    implicit val diversityEthnicityFormats = Json.format[DiversityEthnicity]
    implicit val diversitySocioEconomicFormats = Json.format[DiversitySocioEconomic]
    implicit val diversityGenderAndDisabilityFormats = Json.format[DiversityGender]
    implicit val diversitySexualityFormats = Json.format[DiversitySexuality]
    implicit val diversityReportRowFormats = Json.format[DiversityReportRow]
    implicit val diversityReportFormats = Json.format[DiversityReport]
    implicit val testFormats = Json.format[TestResult]
    implicit val candidateTestReportFormats = Json.format[CandidateTestReport]
    implicit val allocatedCandidateFormats = Json.format[AllocatedCandidate]
    implicit val applicationProgressStatusFormats = Json.format[ApplicationProgressStatus]
    implicit val applicationProgressStatusesFormats = Json.format[ApplicationProgressStatuses]
    implicit val applicationUserFormats = Json.format[ApplicationUser]

    implicit val onlineTestPdfReportFormats = Json.format[OnlineTestPDFReport]
  }
}
