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

package model.persisted.assessor

import model.persisted.eventschedules.Location
import org.joda.time.LocalDate
import play.api.libs.json.Json
import reactivemongo.bson.Macros
import repositories.BSONLocalDateHandler

case class AssessorAvailability(
  location: Location,
  date: LocalDate
)

object AssessorAvailability {
  implicit val persistedAssessorAvailabilityFormat = Json.format[AssessorAvailability]
  implicit val persistedAssessorAvailabilityHandler = Macros.handler[AssessorAvailability]

  def toAvailabilityMap(o: Seq[AssessorAvailability]): Map[String, List[LocalDate]] = o groupBy (_.location) map {
    case (location, avail) =>
      location.name -> avail.map(_.date).toList
  }
}
