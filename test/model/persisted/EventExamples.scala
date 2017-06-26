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

package model.persisted

import controllers.DayAggregateEvent
import factories.UUIDFactory
import model.persisted.eventschedules.{ Event, EventType, VenueType }
import org.joda.time.{ LocalDate, LocalTime }

object EventExamples {

  val EventsNew = List(
    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "PDFS FSB", location = "London",
      venue = VenueType.LONDON_FSAC, date = LocalDate.parse("2017-06-20"), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "GCFS FSB", location = "London",
      venue = VenueType.LONDON_FSAC, date = LocalDate.parse("2017-06-20"), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map("QAC" -> 18)),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.TELEPHONE_INTERVIEW, description = "ORAC", location = "London",
      venue = VenueType.LONDON_FSAC, date = LocalDate.parse("2017-06-20"), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.SKYPE_INTERVIEW, description = "GCFS FSB", location = "Newcastle",
      venue = VenueType.NEWCASTLE_LONGBENTON, date = LocalDate.parse("2017-06-20"), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map("ASSESSOR" -> 1)),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "PDFS FSB", location = "Newcastle",
      venue = VenueType.NEWCASTLE_LONGBENTON, date = LocalDate.parse("2017-06-20"), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map())

  )

  val DayAggregateEventsNew = List(
    DayAggregateEvent(new LocalDate("2017-06-20"), "London"),
    DayAggregateEvent(new LocalDate("2017-06-20"), "Newcastle")
  )

}
