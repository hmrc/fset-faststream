/*
 * Copyright 2020 HM Revenue & Customs
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

package model.testdata

import model.SchemeId
import model.command.testdata.CreateAdminRequest.CreateAdminRequest
import model.exchange.AssessorAvailability
import model.persisted.assessor.AssessorStatus
import model.persisted.assessor.AssessorStatus.AssessorStatus
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker

object CreateAdminData {

  case class CreateAdminData(email: String, firstName: String, lastName: String,
                             preferredName: String, roles: List[String], phone: Option[String],
                             assessor: Option[AssessorData]) extends CreateTestData

  object CreateAdminData {
    def apply(createRequest: CreateAdminRequest, dataFaker: DataFaker)(generatorId: Int): CreateAdminData = {

      val roles = createRequest.roles.getOrElse(List("admin"))
      val rolesStr = roles.mkString("_")
      val username = s"test_${rolesStr}_${createRequest.emailPrefix.getOrElse(dataFaker.Random.number(Some(10000)))}a$generatorId"
      val firstName = createRequest.firstName.getOrElse(s"$rolesStr$generatorId")
      val lastName = createRequest.lastName.getOrElse(s"$rolesStr$generatorId")
      val preferredName = createRequest.preferredName.getOrElse(s"$rolesStr$generatorId")
      val phone = createRequest.phone

      val assessorData = if (roles contains "assessor") {
        val skills = createRequest.assessor.flatMap(_.skills).getOrElse(dataFaker.skills)
        val sifterSchemes = createRequest.assessor.flatMap(_.sifterSchemes).getOrElse(dataFaker.sifterSchemes)
        val civilServant = createRequest.assessor.flatMap(_.civilServant).getOrElse(dataFaker.Random.bool)
        val availability: Option[Set[AssessorAvailability]] = createRequest.assessor.flatMap(assessorRequest => {
            assessorRequest.availability.map { assessorAvailabilityRequests => {
              assessorAvailabilityRequests.map { assessorAvailabilityRequest => {
                AssessorAvailability.apply(assessorAvailabilityRequest)
              }
            }}}}).orElse(dataFaker.Assessor.availability)
        Some(AssessorData(skills, sifterSchemes, civilServant, availability,
          createRequest.assessor.map(_.status).getOrElse(AssessorStatus.CREATED)))
      } else {
        None
      }
      CreateAdminData(s"$username@mailinator.com", firstName, lastName, preferredName, roles, phone, assessorData)
    }
  }

  case class AssessorData(
    skills: List[String],
    sifterSchemes: List[SchemeId],
    civilServant: Boolean,
    availability: Option[Set[AssessorAvailability]],
    status: AssessorStatus
  )

  object AssessorData {
    implicit val assessorDataFormat: OFormat[AssessorData] = Json.format[AssessorData]
  }
}
