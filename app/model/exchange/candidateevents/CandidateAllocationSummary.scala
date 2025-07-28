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

package model.exchange.candidateevents

import model.{AllocationStatuses, SchemeId}
import model.persisted.eventschedules.EventType.EventType
import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class CandidateAllocationSummary(
  eventType: EventType,
  eventDescription: String,
  eventDate: LocalDate,
  sessionDescription: String,
  allocationStatus: AllocationStatuses.AllocationStatus,
  removeReason: Option[CandidateRemoveReason],
  // Only FSB events will have an associated scheme
  eventScheme: Option[SchemeId]
)

object CandidateAllocationSummary {
  implicit val allocationEventSummaryFormat: OFormat[CandidateAllocationSummary] = Json.format[CandidateAllocationSummary]
}
