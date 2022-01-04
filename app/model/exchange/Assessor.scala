/*
 * Copyright 2022 HM Revenue & Customs
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

import model.SchemeId
import model.command.testdata.CreateAdminRequest.AssessorAvailabilityRequest
import model.persisted.assessor.AssessorStatus.AssessorStatus
import org.joda.time.LocalDate
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.Json

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

case class Assessor(
                     userId: String,
                     version: Option[String],
                     skills: List[String],
                     sifterSchemes: List[SchemeId],
                     civilServant: Boolean,
                     status: AssessorStatus,
                     // Please note that this was originally a Seq but during the migration from Play2.6 -> Play2.7 the default scala collection
                     // that play deserializes to changed from a List to a Vector so have changed to a List here to force it back. It caused the
                     // AssessorControllerSpec to break when using the Seq
                     availabilities: List[AssessorAvailability] = Nil
                   ) {
  override def toString: String =
    s"userId:$userId," +
      s"version:$version," +
      s"skills:$skills," +
      s"sifterSchemes:$sifterSchemes," +
      s"civilServant:$civilServant," +
      s"status:$status," +
      s"availabilities:$availabilities"
}

object Assessor {
  implicit val assessorFormat = Json.format[Assessor]

  def apply(a: model.persisted.assessor.Assessor): Assessor =
    Assessor(a.userId, a.version, a.skills, a.sifterSchemes, a.civilServant, a.status, a.availability.map(AssessorAvailability.apply).toList)
}

case class AssessorAvailabilities(
                                   userId: String,
                                   version: Option[String],
                                   availabilities: Set[AssessorAvailability]
                                 )

object AssessorAvailabilities {
  implicit val assessorAvailabilityFormat = Json.format[AssessorAvailabilities]

  def apply(assessor: model.persisted.assessor.Assessor): AssessorAvailabilities =
    AssessorAvailabilities(assessor.userId, assessor.version, assessor.availability.map(a => AssessorAvailability.apply(a)))
}
