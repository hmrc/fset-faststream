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

package model.testdata

import model.SchemeId
import model.command.testdata.CreateAdminRequest.CreateAdminRequest
import model.exchange.AssessorAvailability
import model.persisted.assessor.AssessorStatus
import org.joda.time.LocalDate
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker
import services.testdata.faker.DataFaker.Random

object CreateAdminData {

  case class CreateAdminData(email: String, firstName: String, lastName: String,
                             preferredName: String, role: String, phone: Option[String],
                             assessor: Option[AssessorData]) extends CreateTestData

  object CreateAdminData {
    def apply(createRequest: CreateAdminRequest)(generatorId: Int): CreateAdminData = {

      val role = createRequest.role.getOrElse("admin")
      val username = s"test_${role}_${createRequest.emailPrefix.getOrElse(Random.number(Some(10000)))}a$generatorId"
      val firstName = createRequest.firstName.getOrElse(s"$role$generatorId")
      val lastName = createRequest.lastName.getOrElse(s"$role$generatorId")
      val preferredName = createRequest.preferredName.getOrElse(s"$role$generatorId")
      val phone = createRequest.phone

      val assessorData = if (role == "assessor") {
        val skills = createRequest.assessor.flatMap(_.skills).getOrElse(DataFaker.Random.skills)
        val sifterSchemes = createRequest.assessor.flatMap(_.sifterSchemes).getOrElse(DataFaker.Random.sifterSchemes)
        val civilServant = createRequest.assessor.flatMap(_.civilServant).getOrElse(DataFaker.Random.bool)
        val availability1: Option[List[AssessorAvailability]] = createRequest.assessor.flatMap(assessorRequest => {
            assessorRequest.availability.map { assessorAvailabilityRequests => {
              assessorAvailabilityRequests.map { assessorAvailabilityRequest => {
                AssessorAvailability.apply(assessorAvailabilityRequest)
              }
            }}}})
        val availability = createRequest.assessor match {
          case Some(assessorRequest) if assessorRequest.status == AssessorStatus.AVAILABILITIES_SUBMITTED && availability1.isEmpty =>
            DataFaker.Random.Assessor.availability
          case None => availability1
        }
        Some(AssessorData(skills, sifterSchemes, civilServant, availability))
      } else {
        None
      }
      CreateAdminData(s"$username@mailinator.com", firstName, lastName, preferredName, role, phone, assessorData)
    }

  }

  case class AssessorData(skills: List[String], sifterSchemes: List[SchemeId], civilServant: Boolean,
                          availability: Option[List[AssessorAvailability]])

  object AssessorData {
    implicit val assessorDataFormat: OFormat[AssessorData] = Json.format[AssessorData]
  }

}
