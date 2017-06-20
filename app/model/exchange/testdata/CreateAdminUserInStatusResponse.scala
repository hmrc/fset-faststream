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

import model.testdata.CreateAdminUserInStatusData.AssessorData
import org.joda.time.LocalDate
import play.api.libs.json.{ Json, OFormat }

object CreateAdminUserInStatusResponse {

  case class CreateAdminUserInStatusResponse(generationId: Int, userId: String,
                                             preferedName: Option[String], email: String,
                                             firstName: String, lastName: String, phone: Option[String] = None,
                                             assessor: Option[AssessorResponse] = None) extends CreateTestDataResponse

  object CreateAdminUserInStatusResponse {
    implicit val createAdminUserInStatusResponseFormat: OFormat[CreateAdminUserInStatusResponse] =
      Json.format[CreateAdminUserInStatusResponse]
  }

  case class AssessorResponse(skills: List[String], civilServant: Boolean, availability: Map[String, List[LocalDate]])

  object AssessorResponse {
    implicit val assessorResponseFormat: OFormat[AssessorResponse] = Json.format[AssessorResponse]

    def apply(exchange: model.exchange.Assessor): AssessorResponse = {
      AssessorResponse(exchange.skills, exchange.civilServant, Map.empty)
    }

    def apply(persisted: model.persisted.assessor.Assessor): AssessorResponse = {
      AssessorResponse(persisted.skills, persisted.civilServant, persisted.availability)
    }

    def apply(data: AssessorData): AssessorResponse = {
      // TODO: We should add availability in AssessorData
      AssessorResponse(data.skills, data.civilServant, Map.empty)
    }
  }

}
