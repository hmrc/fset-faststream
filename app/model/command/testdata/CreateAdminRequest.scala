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

package model.command.testdata

import model.SchemeId
import model.exchange.AssessorAvailability
import model.persisted.assessor.AssessorStatus
import model.persisted.assessor.AssessorStatus.AssessorStatus
import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

object CreateAdminRequest {

  case class CreateAdminRequest(emailPrefix: Option[String], firstName: Option[String], lastName: Option[String],
                                preferredName: Option[String], roles: Option[List[String]], phone: Option[String],
                                assessor: Option[AssessorRequest]) extends CreateTestDataRequest {
  }

  object CreateAdminRequest {
    implicit val createAdminRequestFormat: OFormat[CreateAdminRequest] = Json.format[CreateAdminRequest]
  }

  case class AssessorRequest(skills: Option[List[String]] = None,
                             sifterSchemes: Option[List[SchemeId]] = None,
                             civilServant: Option[Boolean] = None,
                             availability: Option[Set[AssessorAvailabilityRequest]] = None,
                             status: AssessorStatus = AssessorStatus.CREATED)

  object AssessorRequest {
    implicit val assessorTestDataFormat: OFormat[AssessorRequest] = Json.format[AssessorRequest]
  }

  case class AssessorAvailabilityRequest(location: String, date: LocalDate)

  object AssessorAvailabilityRequest {
    implicit val assessorAvailabilityRequestFormat: OFormat[AssessorAvailabilityRequest] = Json.format[AssessorAvailabilityRequest]

    def apply(exchange: AssessorAvailability): AssessorAvailabilityRequest = {
      AssessorAvailabilityRequest(exchange.location, exchange.date)
    }
  }
}
