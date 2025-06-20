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

package model.persisted.eventschedules

import model.exchange.{Session => ExchangeSession}
import factories.UUIDFactory
import play.api.libs.json.{Json, OFormat}
import repositories.events.SessionConfig

import java.time.LocalTime

case class Session(
  id: String,
  description: String,
  capacity: Int,
  minViableAttendees: Int,
  attendeeSafetyMargin: Int,
  startTime: LocalTime,
  endTime: LocalTime
) {
  override def toString: String =
    "Session(" +
      s"id=$id," +
      s"description=$description," +
      s"capacity=$capacity," +
      s"minViableAttendees=$minViableAttendees," +
      s"attendeeSafetyMargin=$attendeeSafetyMargin," +
      s"startTime=$startTime," +
      s"endTime=$endTime" +
      ")"
}

case class UpdateSession(id: String, capacity: Int, minViableAttendees: Int, attendeeSafetyMargin: Int, startTime: LocalTime, endTime: LocalTime)

object UpdateSession {
  implicit val format: OFormat[UpdateSession] = Json.format[UpdateSession]
}

object Session {
  implicit val sessionFormat: OFormat[Session] = Json.format[Session]

  def apply(s: SessionConfig): Session = {
    Session(UUIDFactory.generateUUID(), s.description, s.capacity, s.minViableAttendees, s.attendeeSafetyMargin, s.startTime, s.endTime)
  }

  def apply(exchangeSession: ExchangeSession): Session = {
    Session(id = exchangeSession.id,
      description = exchangeSession.description,
      capacity = exchangeSession.capacity,
      minViableAttendees = exchangeSession.minViableAttendees,
      attendeeSafetyMargin = exchangeSession.attendeeSafetyMargin,
      startTime = exchangeSession.startTime,
      endTime = exchangeSession.endTime)
  }
}
