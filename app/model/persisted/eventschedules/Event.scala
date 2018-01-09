/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.Macros
import model.exchange.{ Event => ExchangeEvent }
import repositories.{ BSONLocalDateHandler, BSONLocalTimeHandler, BSONDateTimeHandler, BSONMapStringIntHandler }

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
  createdAt: DateTime,
  skillRequirements: Map[String, Int],
  sessions: List[Session],
  wasBulkUploaded: Boolean = false
)

case class UpdateEvent(id: String, skillRequirements: Map[String, Int], sessions: Seq[UpdateSession]) {
  def session(sessionId: String): UpdateSession = {
    sessions.find(_.id == sessionId).getOrElse(throw new Exception(s"Unable to find session with ID $sessionId"))
  }
}

object UpdateEvent {
  implicit val format = Json.format[UpdateEvent]
}

object Event {
  implicit val eventFormat: OFormat[Event] = Json.format[Event]
  implicit val eventHandler = Macros.handler[Event]

  implicit def eventsToDistinctT(events: Seq[Event]) = new {
    def distinctTransform[S, T](uniqueness: Event => S, transformer: Event => T)
      (implicit cbf: scala.collection.generic.CanBuildFrom[Seq[Event], T, Seq[T]]) = {
      val builder = cbf()
      val seenAlready = scala.collection.mutable.HashSet[S]()
      for (event <- events) {
        if (!seenAlready(uniqueness(event))){
          builder += transformer(event)
          seenAlready += uniqueness(event)
        }
      }
      builder.result()
    }
  }

  def apply(exchangeEvent: ExchangeEvent): Event = {
    new Event(
      id = exchangeEvent.id,
      eventType = exchangeEvent.eventType,
      description = exchangeEvent.description,
      location = exchangeEvent.location,
      venue = exchangeEvent.venue,
      date = exchangeEvent.date,
      capacity = exchangeEvent.capacity,
      minViableAttendees = exchangeEvent.minViableAttendees,
      attendeeSafetyMargin = exchangeEvent.attendeeSafetyMargin,
      startTime = exchangeEvent.startTime,
      endTime = exchangeEvent.endTime,
      createdAt = DateTime.now(),
      skillRequirements = exchangeEvent.skillRequirements,
      sessions = exchangeEvent.sessions.map(Session.apply)
    )
  }
}
