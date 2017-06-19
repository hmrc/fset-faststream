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

package controllers

import model.persisted.eventschedules.{ Event, SkillType }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.Json
import play.api.mvc.Action
import reactivemongo.bson.Macros
import repositories.events.EventsRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object DayAggregateEventController extends DayAggregateEventController {
  override val eventsRepository: EventsRepository = repositories.eventsRepository

}

case class DayAggregateEvent(date: LocalDate, location: String)

object DayAggregateEvent {
  implicit val dayAggregateEventFormat = Json.format[DayAggregateEvent]
}

trait DayAggregateEventController extends BaseController {
  val eventsRepository: EventsRepository

  def findBySkills(skills: String) = Action.async { implicit request =>
    find(Some(skills), None)
  }

  def findBySkillsAndLocation(skills: String, location: String) = Action.async { implicit request =>
    find(Some(skills), Some(location))
  }

  private def find(skills: Option[String], location: Option[String]) = {
    val skillsList = skills.map(_.split(",").toList)
    eventsRepository.fetchEvents(None, None, location, skillsList)
      .map { events =>
        val dayAggregateDays = events.groupBy(e => DayAggregateEvent(e.date, e.location)).keys.toList
        Ok(Json.toJson(dayAggregateDays))
      }
  }
}
