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

package model.exchange

import org.joda.time.LocalTime
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Json, OFormat }

case class Session(
  id: String,
  description: String,
  capacity: Int,
  minViableAttendees: Int,
  attendeeSafetyMargin: Int,
  startTime: LocalTime,
  endTime: LocalTime)

object Session {
  implicit val format: OFormat[Session] = Json.format[Session]

  def apply(persistedSession: model.persisted.eventschedules.Session): Session = {
    Session(
      id = persistedSession.id,
      description = persistedSession.description,
      capacity = persistedSession.capacity,
      minViableAttendees = persistedSession.minViableAttendees,
      attendeeSafetyMargin = persistedSession.attendeeSafetyMargin,
      startTime = persistedSession.startTime,
      endTime = persistedSession.endTime)
  }
}
