/*
 * Copyright 2017 HM Revenue & Customs
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

import model.Commands.ApplicationAssessment
import model.command.GeneralDetails
import model.persisted.{ AssistanceDetails, _ }
import model.{ Adjustments, SelectedSchemes }
import org.joda.time.LocalDate
import play.api.libs.json.{ Json, OFormat }

object DataGenerationResponse {

  case class DataGenerationResponse(
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
                                     applicationAssessment: Option[ApplicationAssessment] = None,
                                     schemePreferences: Option[SelectedSchemes] = None,
                                     accessCode: Option[String] = None,
                                     adjustmentInformation: Option[Adjustments] = None,
                                     assessor: Option[AssessorResponse] = None
  )

  object DataGenerationResponse {
    implicit val dataGenerationResponseFormat: OFormat[DataGenerationResponse] =
      Json.format[DataGenerationResponse]
  }

  case class TestGroupResponse(tests: List[TestResponse], schemeResult: Option[PassmarkEvaluation])
  object TestGroupResponse { implicit val testGroupResponseFormat: OFormat[TestGroupResponse] = Json.format[TestGroupResponse] }

  case class TestResponse(testId: Int, testType: String, token: String, testUrl: String)
  object TestResponse { implicit val testResponseFormat: OFormat[TestResponse] = Json.format[TestResponse] }

  case class AssessorResponse(userId: String, skills: List[String], civilServant: Boolean, availability: Map[String, List[LocalDate]])
  object AssessorResponse {
    implicit val assessorResponseFormat: OFormat[AssessorResponse] = Json.format[AssessorResponse]

    def apply(exchange: model.exchange.Assessor): AssessorResponse = {
      AssessorResponse(exchange.userId, exchange.skills, exchange.civilServant, Map.empty)
    }

    def apply(persisted: model.persisted.Assessor): AssessorResponse = {
      AssessorResponse(persisted.userId, persisted.skills, persisted.civilServant, persisted.availability)
    }
  }

}
