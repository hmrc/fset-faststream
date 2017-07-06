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

package model.exchange

import model.command.testdata.CreateAdminRequest.AssessorAvailabilityRequest
import org.joda.time.LocalDate
import play.api.libs.json.Json

case class Assessor(userId: String, skills: List[String], sifterSchemes: List[String], civilServant: Boolean)

object Assessor {
  implicit val assessorFormat = Json.format[Assessor]

  def apply(assessor: model.persisted.assessor.Assessor): Assessor =
    Assessor(assessor.userId, assessor.skills, assessor.sifterSchemes, assessor.civilServant)
}

case class AssessorAvailability(location: String, date: LocalDate)

object AssessorAvailability {
  implicit val assessorAvailabilityFormat = Json.format[AssessorAvailability]

  def apply(persisted: model.persisted.assessor.AssessorAvailability): AssessorAvailability = {
    AssessorAvailability(persisted.location.name, persisted.date)
  }

  def apply(request: AssessorAvailabilityRequest): AssessorAvailability = {
    AssessorAvailability(request.location, request.date)
  }
}

case class AssessorAvailabilities(userId: String, availability: List[AssessorAvailability])

object AssessorAvailabilities {
  implicit val assessorAvailabilityFormat = Json.format[AssessorAvailabilities]

  def apply(assessor: model.persisted.assessor.Assessor): AssessorAvailabilities =
    AssessorAvailabilities(assessor.userId, assessor.availability.map(a => AssessorAvailability.apply(a)))
}
