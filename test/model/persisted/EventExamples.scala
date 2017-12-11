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
import model.UniqueIdentifier
import model.persisted.eventschedules._
import org.joda.time.{ DateTime, LocalDate, LocalTime }

object EventExamples {
  val VenueAll = Venue("ALL_VENUES", "All venues")
  val VenueLondon = Venue("London FSAC", "Bush House")
  val VenueNewcastle = Venue("Newcastle FSAC", "Longbenton")

  val LocationAll = Location("All")
  val LocationLondon = Location("London")
  val LocationNewcastle = Location("Newcastle")

  val e1 = Event(id = UniqueIdentifier.randomUniqueIdentifier.toString(), eventType = EventType.FSAC, description = "",
    location = LocationLondon, venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
    attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
    createdAt = DateTime.now, skillRequirements = Map(), sessions = List())

  val e2 = Event(id = UniqueIdentifier.randomUniqueIdentifier.toString(), eventType = EventType.FSB, description = "ORAC",
    location = LocationLondon, venue = VenueLondon, date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
    attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = DateTime.now,
    skillRequirements = Map(), sessions = List())

  val e1Session1Id = UniqueIdentifier.randomUniqueIdentifier.toString()
  val session2Id = UniqueIdentifier.randomUniqueIdentifier.toString()

  val e1WithSession = e1.copy(sessions = List(Session(e1Session1Id,
  "session1 desc", 8, 5, 1, LocalTime.now(), LocalTime.now().plusHours(4))))

  val e1WithSessions = e1.copy(sessions = List(
    Session(e1Session1Id, "session1 desc", 8, 5, 1, LocalTime.now(), LocalTime.now().plusHours(4)),
    Session(session2Id, "session2 desc", 8, 5, 1, LocalTime.now(), LocalTime.now().plusHours(4))
  ))

  val EventsNew = List(
    e1,
    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, description = "", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(), sessions = List()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSB, description = "ORAC", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(), sessions = List()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSB, description = "GCFS FSB", location = LocationNewcastle,
      venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.ASSESSOR.toString -> 1), sessions = List()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSB, description = "EAC", location = LocationNewcastle,
      venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 1), sessions = List())

  )

  val DayAggregateEventsNew = List(
    DayAggregateEvent(LocalDate.now, LocationLondon),
    DayAggregateEvent(LocalDate.now, Location("Home")),
    DayAggregateEvent(LocalDate.now, LocationNewcastle)
  )

  val eventCreatedAt = DateTime.parse("2017-08-01T00:00:01Z")
  val YamlEvents = List(
    Event("1", EventType.withName("FSAC"), "PDFS FSB", Location("London"), Venue("london fsac", "bush house"),
      LocalDate.parse("2017-04-03"), 36, 12, 2, LocalTime.parse("11:00:00.000"), LocalTime.parse("12:00:00.000"), eventCreatedAt,
      Map("DEPARTMENTAL_ASSESSOR" -> 3, "EXERCISE_MARKER" -> 3, "ASSESSOR" -> 6, "QUALITY_ASSURANCE_COORDINATOR" -> 1, "CHAIR" -> 3),
      List(Session("", "AM", 36, 12, 4, LocalTime.parse("11:00:00.000"), LocalTime.parse("12:00:00.000")))
    ),
    Event("2", EventType.withName("FSAC"), "PDFS FSB", Location("London"), Venue("london fsac", "bush house"),
      LocalDate.parse("2017-04-03"), 36, 12, 2, LocalTime.parse("09:00:00.000"), LocalTime.parse("12:00:00.000"), eventCreatedAt,
      Map("DEPARTMENTAL_ASSESSOR" -> 3, "EXERCISE_MARKER" -> 2, "ASSESSOR" -> 6, "QUALITY_ASSURANCE_COORDINATOR" -> 1, "CHAIR" -> 3),
      List(
        Session("1", "First", 36, 12, 4, LocalTime.parse("09:00:00.000"), LocalTime.parse("10:30:00.000")),
        Session("2", "Second", 36, 12, 4, LocalTime.parse("10:30:00.000"), LocalTime.parse("12:00:00.000")))
    ),
    Event("3", EventType.withName("FSAC"), "PDFS FSB", Location("London"), Venue("london fsac", "bush house"),
      LocalDate.parse("2017-04-03"), 36, 12, 2, LocalTime.parse("09:00:00.000"), LocalTime.parse("12:00:00.000"), eventCreatedAt,
      Map("DEPARTMENTAL_ASSESSOR" -> 2, "EXERCISE_MARKER" -> 3, "ASSESSOR" -> 6, "QUALITY_ASSURANCE_COORDINATOR" -> 1, "CHAIR" -> 3),
      List(
        Session("1", "First", 36, 12, 4, LocalTime.parse("09:00:00.000"), LocalTime.parse("10:30:00.000")),
        Session("2", "Second", 36, 12, 4, LocalTime.parse("10:30:00.000"), LocalTime.parse("12:00:00.000")))
    )
  )
}
