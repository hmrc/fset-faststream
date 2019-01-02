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

package model.exchange.testdata

import model.command.GeneralDetails
import model.exchange.testdata.CreateAdminResponse.AssessorResponse
import model.persisted.{ AssistanceDetails, _ }
import model.{ Adjustments, SchemeId, SelectedSchemes }
import play.api.libs.json.{ Json, OFormat }

object CreateCandidateResponse {

  case class CreateCandidateResponse(
    generationId: Int,
    userId: String,
    applicationId: Option[String],
    email: String,
    firstName: String,
    lastName: String,
    mediaReferrer: Option[String] = None,
    personalDetails: Option[GeneralDetails] = None,
    diversityDetails: Option[List[QuestionnaireQuestion]] = None,
    assistanceDetails: Option[AssistanceDetails] = None,
    phase1TestGroup: Option[TestGroupResponse] = None,
    phase2TestGroup: Option[TestGroupResponse] = None,
    phase3TestGroup: Option[TestGroupResponse] = None,
    fsbTestGroup: Option[FsbTestGroupResponse] = None,
    siftForms: Option[Seq[SiftForm]] = None,
    schemePreferences: Option[SelectedSchemes] = None,
    accessCode: Option[String] = None,
    adjustmentInformation: Option[Adjustments] = None,
    assessor: Option[AssessorResponse] = None
  ) extends CreateTestDataResponse

  object CreateCandidateResponse {
    implicit val createCandidateResponseFormat: OFormat[CreateCandidateResponse] =
      Json.format[CreateCandidateResponse]
  }

  case class FsbTestGroupResponse(results: Seq[SchemeEvaluationResult])

  object FsbTestGroupResponse {
    implicit val fsbTestGroupResponse: OFormat[FsbTestGroupResponse] = Json.format[FsbTestGroupResponse]
  }

  case class TestGroupResponse(tests: List[TestResponse], schemeResult: Option[PassmarkEvaluation])

  object TestGroupResponse {
    implicit val testGroupResponseFormat: OFormat[TestGroupResponse] = Json.format[TestGroupResponse]
  }

  case class TestResponse(testId: Int, testType: String, token: String, testUrl: String)

  object TestResponse {
    implicit val testResponseFormat: OFormat[TestResponse] = Json.format[TestResponse]
  }

  case class SiftForm(
    scheme: SchemeId,
    form: String,
    siftResult: Option[PassmarkEvaluation]
  )

  object SiftForm {
    implicit val siftFormFormat: OFormat[SiftForm] = Json.format[SiftForm]
  }
}
