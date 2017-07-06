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
import model.persisted.eventschedules.{ Event, EventType, SkillType, Venue, Location }
import org.joda.time.{ LocalDate, LocalTime }

object EventExamples {
  val VenueAll = Venue("ALL_VENUES", "All venues")
  val VenueLondon = Venue("London FSAC", "Bush House")
  val VenueNewcastle = Venue("Newcastle FSAC", "Longbenton")

  val LocationAll = Location("All")
  val LocationLondon = Location("London")
  val LocationNewcastle = Location("Newcastle")
  val EventsNew = List(
    Event(id = "1", eventType = EventType.FSAC, description = "PDFS FSB", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "GCFS FSB", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.TELEPHONE_INTERVIEW, description = "ORAC", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.SKYPE_INTERVIEW, description = "GCFS FSB", location = LocationNewcastle,
      venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map(SkillType.ASSESSOR.toString -> 1)),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "DFS FSB", location = LocationNewcastle,
      venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map(
        "QAC" -> 1
      ))

  )

  val DayAggregateEventsNew = List(
    DayAggregateEvent(LocalDate.now, LocationLondon),
    DayAggregateEvent(LocalDate.now, LocationNewcastle)
  )

}
