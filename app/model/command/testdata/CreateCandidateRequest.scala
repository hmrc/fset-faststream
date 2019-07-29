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

package model.command.testdata

import model.ApplicationRoute.ApplicationRoute
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import model.{Adjustments, SchemeId}
import play.api.libs.json.{Json, OFormat}

object CreateCandidateRequest {

  case class AssistanceDetailsRequest(hasDisability: Option[String] = None,
    hasDisabilityDescription: Option[String] = None,
    setGis: Option[Boolean] = None,
    onlineAdjustments: Option[Boolean] = None,
    onlineAdjustmentsDescription: Option[String] = None,
    assessmentCentreAdjustments: Option[Boolean] = None,
    assessmentCentreAdjustmentsDescription: Option[String] = None,
    phoneAdjustments: Option[Boolean] = None,
    phoneAdjustmentsDescription: Option[String] = None
  )

  object AssistanceDetailsRequest {
    implicit val assistanceDetailsRequestFormat: OFormat[AssistanceDetailsRequest] = Json.format[AssistanceDetailsRequest]
  }

  case class DiversityDetailsRequest(
    genderIdentity: Option[String] = None,
    sexualOrientation: Option[String] = None,
    ethnicity: Option[String] = None,
    universityAttended: Option[String] = None,
    parentalEmployment: Option[String] = None,
    parentalEmployedOrSelfEmployed: Option[String] = None,
    parentalCompanySize: Option[String] = None
  )

  object DiversityDetailsRequest {
    implicit val diversityDetailsRequestFormat: OFormat[DiversityDetailsRequest] = Json.format[DiversityDetailsRequest]
  }

  trait TestDatesRequest {
    def start: Option[String]

    def expiry: Option[String]

    def completion: Option[String]
  }

  trait TestResultRequest {
    def scores: List[String]
  }

  abstract class PhaseXTestDataRequest(
    start: Option[String] = None,
    expiry: Option[String] = None,
    completion: Option[String] = None,
    val scores: List[String] = Nil,
    // TODO: Maybe we don't need TestDatesRequest
    val passmarkEvaluation: Option[PassmarkEvaluation] = None) extends TestDatesRequest

  case class Phase1TestDataRequest(
    start: Option[String] = None,
    expiry: Option[String] = None,
    completion: Option[String] = None,
    override val scores: List[String] = Nil,
    override val passmarkEvaluation: Option[PassmarkEvaluation] = None
  ) extends PhaseXTestDataRequest(start, expiry, completion, scores, passmarkEvaluation)

  object Phase1TestDataRequest {
    implicit val phase1TestDataFormat: OFormat[Phase1TestDataRequest] = Json.format[Phase1TestDataRequest]
  }

  case class Phase2TestDataRequest(
    start: Option[String] = None,
    expiry: Option[String] = None,
    completion: Option[String] = None,
    override val scores: List[String] = Nil,
    override val passmarkEvaluation: Option[PassmarkEvaluation] = None
  ) extends PhaseXTestDataRequest(start, expiry, completion, scores, passmarkEvaluation) with TestResultRequest

  object Phase2TestDataRequest {
    implicit val phase2TestDataFormat: OFormat[Phase2TestDataRequest] = Json.format[Phase2TestDataRequest]
  }

  case class Phase3TestDataRequest(
    start: Option[String] = None,
    expiry: Option[String] = None,
    completion: Option[String] = None,
    override val scores: List[String] = Nil,
    receivedBeforeInHours: Option[Int] = None,
    generateNullScoresForFewQuestions: Option[Boolean] = None,
    override val passmarkEvaluation: Option[PassmarkEvaluation] = None
  ) extends PhaseXTestDataRequest(start, expiry, completion, scores, passmarkEvaluation) with TestResultRequest

  object Phase3TestDataRequest {
    implicit val phase3TestDataFormat: OFormat[Phase3TestDataRequest] = Json.format[Phase3TestDataRequest]
  }

  case class FsbTestGroupDataRequest(results: Seq[SchemeEvaluationResult])

  object FsbTestGroupDataRequest {
    implicit val fsbTestGroupDataFormat: OFormat[FsbTestGroupDataRequest] = Json.format[FsbTestGroupDataRequest]
  }

  case class PersonalDataRequest(
    emailPrefix: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    preferredName: Option[String] = None,
    dateOfBirth: Option[String] = None,
    postCode: Option[String] = None,
    country: Option[String] = None,
    edipCompleted: Option[Boolean] = None
  )

  object PersonalDataRequest {
    implicit val personalDataFormat: OFormat[PersonalDataRequest] = Json.format[PersonalDataRequest]
  }

  case class StatusDataRequest(
    applicationStatus: String = "registered",
    previousApplicationStatus: Option[String] = None,
    progressStatus: Option[String] = Some("registered"),
    applicationRoute: Option[String] = Some("Faststream")
  )

  object StatusDataRequest {
    implicit def statusDataFormat: OFormat[StatusDataRequest] = Json.format[StatusDataRequest]
  }

  case class CreateCandidateRequest(
    statusData: StatusDataRequest = new StatusDataRequest,
    personalData: Option[PersonalDataRequest],
    diversityDetails: Option[DiversityDetailsRequest],
    assistanceDetails: Option[AssistanceDetailsRequest],
    schemeTypes: Option[List[SchemeId]],
    isCivilServant: Option[Boolean],
    internshipTypes: Option[List[String]],
    hasFastPass: Option[Boolean],
    hasDegree: Option[Boolean],
    region: Option[String],
    loc1scheme1EvaluationResult: Option[String],
    loc1scheme2EvaluationResult: Option[String],
    confirmedAllocation: Option[Boolean],
    phase1TestData: Option[Phase1TestDataRequest],
    phase2TestData: Option[Phase2TestDataRequest],
    phase3TestData: Option[Phase3TestDataRequest],
    fsbTestGroupData: Option[FsbTestGroupDataRequest],
    adjustmentInformation: Option[Adjustments] = None
  ) extends CreateTestDataRequest

  object CreateCandidateRequest {
    implicit val createCandidateRequestFormat: OFormat[CreateCandidateRequest] = Json.format[CreateCandidateRequest]

    def create(status: String, progressStatus: Option[String], applicationRoute: Option[ApplicationRoute]): CreateCandidateRequest = {
      CreateCandidateRequest(
        statusData = StatusDataRequest(
          applicationStatus = status,
          progressStatus = progressStatus,
          applicationRoute = applicationRoute.map(_.toString)
        ),
        diversityDetails = None,
        assistanceDetails = None,
        personalData = None,
        schemeTypes = None,
        isCivilServant = None,
        internshipTypes = None,
        hasFastPass = None,
        hasDegree = None,
        region = None,
        loc1scheme1EvaluationResult = None,
        loc1scheme2EvaluationResult = None,
        confirmedAllocation = None,
        phase1TestData = None,
        phase2TestData = None,
        phase3TestData = None,
        fsbTestGroupData = None,
        adjustmentInformation = None
      )
    }
  }

}
