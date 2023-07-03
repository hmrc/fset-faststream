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

package controllers

import javax.inject.{ Inject, Singleton }
import model.persisted.eventschedules.Location
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Json, OFormat }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class DayAggregateEventController @Inject() (cc: ControllerComponents,
                                             locationsWithVenuesRepo: LocationsWithVenuesRepository,
                                             eventsRepository: EventsRepository) extends BackendController(cc) {

  implicit val ec = cc.executionContext

  def findBySkillTypes(skills: Seq[SkillType]): Action[AnyContent] = Action.async {
    find(None, skills).map ( dayAggregateEvents => Ok(Json.toJson(dayAggregateEvents)) )
  }

  def findBySkillTypesAndLocation(location: String, skills: Seq[SkillType]): Action[AnyContent] = Action.async {
    locationsWithVenuesRepo.location(location).flatMap { location =>
      find(Some(location), skills)
    }.map(dayAggregateEvents => Ok(Json.toJson(dayAggregateEvents)))
  }

  private def find(location: Option[Location], skills: Seq[SkillType] = Nil) = {
    eventsRepository.getEvents(None, None, location, skills).map { events =>
      events.distinctTransform(_.date, e => DayAggregateEvent(e.date, Location("Home"))) ++
      events.distinctTransform(e => (e.date, e.location), e => DayAggregateEvent(e.date, e.location))
    }
  }
}

case class DayAggregateEvent(date: LocalDate, location: Location)

object DayAggregateEvent {
  implicit val dayAggregateEventFormat: OFormat[DayAggregateEvent] = Json.format[DayAggregateEvent]
}
