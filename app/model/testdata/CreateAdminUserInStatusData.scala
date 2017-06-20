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

import model.command.testdata.CreateAdminUserInStatusRequest.CreateAdminUserInStatusRequest
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker
import services.testdata.faker.DataFaker.Random

object CreateAdminUserInStatusData {

  case class CreateAdminUserInStatusData(email: String, firstName: String, lastName: String,
                                         preferredName: String, role: String, phone: Option[String],
                                         assessor: Option[AssessorData]) extends CreateTestData

  object CreateAdminUserInStatusData {
    def apply(createRequest: CreateAdminUserInStatusRequest)(generatorId: Int): CreateAdminUserInStatusData = {

      val role = createRequest.role.getOrElse("admin")
      val username = s"test_${role}_${createRequest.emailPrefix.getOrElse(Random.number(Some(10000)))}a$generatorId"
      val firstName = createRequest.firstName.getOrElse(s"$role$generatorId")
      val lastName = createRequest.lastName.getOrElse(s"$role$generatorId")
      val preferredName = createRequest.preferredName.getOrElse(s"$role$generatorId")
      val phone = createRequest.phone

      val assessorData = if (role == "assessor") {
        val skills = createRequest.assessor.flatMap(_.skills).getOrElse(DataFaker.Random.skills)
        val civilServant = createRequest.assessor.flatMap(_.civilServant).getOrElse(DataFaker.Random.bool)
        Some(AssessorData(skills, civilServant))
      } else {
        None
      }
      CreateAdminUserInStatusData(s"$username@mailinator.com", firstName, lastName, preferredName, role, phone, assessorData)
    }

  }

  case class AssessorData(skills: List[String], civilServant: Boolean)

  object AssessorData {
    implicit val assessorDataFormat: OFormat[AssessorData] = Json.format[AssessorData]
  }

}
