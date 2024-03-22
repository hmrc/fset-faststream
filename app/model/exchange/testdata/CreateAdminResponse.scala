/*
 * Copyright 2023 HM Revenue & Customs
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

import model.exchange.AssessorAvailability
import model.persisted.assessor.AssessorStatus.AssessorStatus
import model.testdata.CreateAdminData.AssessorData
import play.api.libs.json.{Json, OFormat}

object CreateAdminResponse {

  case class CreateAdminResponse(generationId: Int, userId: String,
    preferedName: Option[String], email: String,
    firstName: String, lastName: String, phone: Option[String] = None,
    roles: List[String],
    disabled: Boolean,
    assessor: Option[AssessorResponse] = None) extends CreateTestDataResponse

  object CreateAdminResponse {
    implicit val createAdminResponseFormat: OFormat[CreateAdminResponse] =
      Json.format[CreateAdminResponse]
  }

  case class AssessorResponse(skills: List[String], civilServant: Boolean, availability: Set[AssessorAvailability],
    status: AssessorStatus, sifterSchemes: List[String])

  object AssessorResponse {
    implicit val assessorResponseFormat: OFormat[AssessorResponse] = Json.format[AssessorResponse]

    def apply(exchange: model.exchange.Assessor): AssessorResponse = {
      AssessorResponse(exchange.skills, exchange.civilServant, Set.empty, exchange.status, exchange.sifterSchemes.map(_.value))
    }

    def apply(persisted: model.persisted.assessor.Assessor): AssessorResponse = {
      AssessorResponse(persisted.skills, persisted.civilServant,
        persisted.availability.map(a => AssessorAvailability.apply(a)), persisted.status, persisted.sifterSchemes.map(_.value))
    }

    def apply(data: AssessorData): AssessorResponse = {
      AssessorResponse(data.skills, data.civilServant, data.availability.getOrElse(Set.empty), data.status,
        data.sifterSchemes.map(_.value))
    }
  }

}
