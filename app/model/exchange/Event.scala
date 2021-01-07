/*
 * Copyright 2021 HM Revenue & Customs
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

import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Location, Venue }
import org.joda.time.{ LocalDate, LocalTime }
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Json, OFormat }

case class Event(
  id: String,
  eventType: EventType,
  description: String,
  location: Location,
  venue: Venue,
  date: LocalDate,
  capacity: Int,
  minViableAttendees: Int,
  attendeeSafetyMargin: Int,
  startTime: LocalTime,
  endTime: LocalTime,
  skillRequirements: Map[String, Int],
  sessions: List[Session])

object Event {
  implicit val format: OFormat[Event] = Json.format[Event]

  def apply(persistedEvent: model.persisted.eventschedules.Event): Event = {
    new Event(
      id = persistedEvent.id,
      eventType = persistedEvent.eventType,
      description = persistedEvent.description,
      location = persistedEvent.location,
      venue = persistedEvent.venue,
      date = persistedEvent.date,
      capacity = persistedEvent.capacity,
      minViableAttendees = persistedEvent.minViableAttendees,
      attendeeSafetyMargin = persistedEvent.attendeeSafetyMargin,
      startTime = persistedEvent.startTime,
      endTime = persistedEvent.endTime,
      skillRequirements = persistedEvent.skillRequirements,
      sessions = persistedEvent.sessions.map(Session.apply)
    )
  }
}
