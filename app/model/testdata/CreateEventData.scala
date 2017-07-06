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

import model.command.testdata.CreateEventRequest.CreateEventRequest
import model.persisted.eventschedules._
import org.joda.time.{ LocalDate, LocalTime }
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker.Random

object CreateEventData {

  case class CreateEventData(
      id: String,
      eventType: EventType.EventType,
      description: String,
      location: Location,
      venue: Venue,
      date: LocalDate,
      capacity: Int,
      minViableAttendees: Int,
      attendeeSafetyMargin: Int,
      startTime: LocalTime,
      endTime: LocalTime,
      skillRequirements: Map[String, Int]
  ) extends CreateTestData {
    def toEvent: Event = {
      Event(id, eventType, description, location, venue, date, capacity, minViableAttendees,
        attendeeSafetyMargin, startTime, endTime, skillRequirements)
    }
  }

  object CreateEventData {
    implicit val createEventDataFormat: OFormat[CreateEventData] = Json.format[CreateEventData]

    def apply(createRequest: CreateEventRequest)(generatorId: Int): CreateEventData = {

      val id = createRequest.id.getOrElse(Random.Event.id)
      val eventType = createRequest.eventType.getOrElse(Random.Event.eventType)
      val description = createRequest.description.getOrElse(Random.Event.description)
      val location = createRequest.location.map(l => Location(l)).getOrElse(Random.Event.location)
      val venue = createRequest.venue.map(v => Venue(v, s"$v")).getOrElse(Random.Event.venue)
      val date = createRequest.date.getOrElse(Random.Event.date)
      val capacity = createRequest.capacity.getOrElse(Random.Event.capacity)
      val minViableAttendees = createRequest.minViableAttendees.getOrElse(Random.Event.minViableAttendees)
      val attendeeSafetyMargin = createRequest.attendeeSafetyMargin.getOrElse(Random.Event.attendeeSafetyMargin)
      val startTime = createRequest.startTime.getOrElse(Random.Event.startTime)
      val endTime = createRequest.endTime.getOrElse(Random.Event.endTime)
      val skillRequirements = createRequest.skillRequirements.getOrElse(Random.Event.skillRequirements)

      CreateEventData(id, eventType, description, location, venue, date, capacity, minViableAttendees, attendeeSafetyMargin,
        startTime, endTime, skillRequirements)
    }
  }

}
