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

package model.command

import model.AllocationStatuses.AllocationStatus
import model.exchange.AssessorSkill
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Location, Venue }
import org.joda.time.{ LocalDate, LocalTime }
import play.api.libs.json.Json

case class AllocationWithEvent(
  assessorId: String,
  eventId: String,
  date: LocalDate,
  startTime: LocalTime,
  endTime: LocalTime,
  venue: Venue,
  location: Location,
  eventType: EventType,
  status: AllocationStatus,
  allocatedAs: AssessorSkill
)

object AllocationWithEvent {
  implicit val allocationWithEventFormat = Json.format[AllocationWithEvent]
}

