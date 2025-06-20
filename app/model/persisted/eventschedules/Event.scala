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

package  model.persisted.eventschedules

import model.exchange.{Event => ExchangeEvent}
import model.persisted.eventschedules.EventType.EventType
import play.api.libs.json._

import java.time.{LocalDate, LocalTime, OffsetDateTime}

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
  createdAt: OffsetDateTime,
  skillRequirements: Map[String, Int],
  sessions: List[Session],
  wasBulkUploaded: Boolean = false
)

object Event {
  // Needed to handle storing DateTime in ISODate format in Mongo
  import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
  implicit val eventFormat: OFormat[Event] = Json.format[Event]

  extension (events: Seq[Event])
    def distinctTransform[S, T](uniqueness: Event => S, transformer: Event => T)
      (implicit bf: scala.collection.BuildFrom[Seq[Event], T, Seq[T]]): Seq[T] = {
      val builder = bf.newBuilder(Seq.empty)
      val seenAlready = scala.collection.mutable.HashSet[S]()
      for (event <- events) {
        if (!seenAlready(uniqueness(event))) {
          builder += transformer(event)
          seenAlready += uniqueness(event)
        }
      }
      builder.result()
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
      createdAt = OffsetDateTime.now,
      skillRequirements = exchangeEvent.skillRequirements,
      sessions = exchangeEvent.sessions.map(Session.apply)
    )
  }
}

case class UpdateEvent(id: String, skillRequirements: Map[String, Int], sessions: Seq[UpdateSession]) {
  def session(sessionId: String): UpdateSession = {
    sessions.find(_.id == sessionId).getOrElse(throw new Exception(s"Unable to find session with ID $sessionId"))
  }
}

object UpdateEvent {
  implicit val format: OFormat[UpdateEvent] = Json.format[UpdateEvent]
}
