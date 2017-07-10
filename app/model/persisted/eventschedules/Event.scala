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

package model.persisted.eventschedules

import model.persisted.eventschedules.EventType.EventType
import org.joda.time.{ LocalDate, LocalTime }
import play.api.libs.json.Json
import reactivemongo.bson.Macros
import repositories.{ BSONLocalDateHandler, BSONLocalTimeHandler, BSONMapHandler }

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
  sessions: List[Session]
)

object Event {
  implicit val eventFormat = Json.format[Event]
  implicit val eventHandler = Macros.handler[Event]
  // scalastyle:off
  def load(id: String,
              eventType: String,
              description: String,
              location: String,
              venue: String,
              date: LocalDate,
              capacity: Int,
              minViableAttendees: Int,
              attendeeSafetyMargin: Int,
              startTime: LocalTime,
              endTime: LocalTime,
              skillRequirements: String,
              sessions: List[Session]) =
    Event(id,
      EventType.withName(eventType),
      description,
      Location(location),
      Venue(venue, ""),
      date,
      capacity,
      minViableAttendees,
      attendeeSafetyMargin, startTime, endTime, Map.empty, sessions)
  // scalastyle:on
}
