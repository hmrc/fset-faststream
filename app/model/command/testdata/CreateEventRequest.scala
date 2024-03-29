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

package model.command.testdata

import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.Session
import play.api.libs.json.{Json, OFormat}

import java.time.{LocalDate, LocalTime}

case class CreateEventRequest(id: Option[String],
  eventType: Option[EventType],
  description: Option[String],
  location: Option[String],
  venue: Option[String],
  date: Option[LocalDate],
  capacity: Option[Int],
  minViableAttendees: Option[Int],
  attendeeSafetyMargin: Option[Int],
  startTime: Option[LocalTime],
  endTime: Option[LocalTime],
  skillRequirements: Option[Map[String, Int]],
  sessions: Option[List[Session]]) extends CreateTestDataRequest {

  override def toString =
    s"id=$id," +
      s"eventType=$eventType," +
      s"description=$description," +
      s"location=$location," +
      s"venue=$venue," +
      s"date=$date," +
      s"capacity=$capacity," +
      s"minViableAttendees=$minViableAttendees," +
      s"attendeeSafetyMargin=$attendeeSafetyMargin," +
      s"startTime=$startTime," +
      s"endTime=$endTime," +
      s"skillRequirements=$skillRequirements," +
      s"sessions=$sessions"
}

object CreateEventRequest {
  def random: CreateEventRequest = new CreateEventRequest(None, None, None, None, None, None, None, None, None, None, None, None, None)
  implicit val createEventRequestRequestFormat: OFormat[CreateEventRequest] = Json.format[CreateEventRequest]
}
