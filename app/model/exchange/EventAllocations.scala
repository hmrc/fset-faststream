/*
 * Copyright 2020 HM Revenue & Customs
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

import model.UniqueIdentifier
import model.persisted.eventschedules.{ Event => PersistedEvent }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.Json


case class CandidateAllocationPerSession(sessionId: UniqueIdentifier, confirmed: Int)

object CandidateAllocationPerSession {
  implicit val format = Json.format[CandidateAllocationPerSession]
}

case class EventAssessorAllocationsSummaryPerSkill(skillType: SkillType, allocated: Int, confirmed: Int)

object EventAssessorAllocationsSummaryPerSkill {
  implicit val format = Json.format[EventAssessorAllocationsSummaryPerSkill]
}

case class EventWithAllocationsSummary(
  date: LocalDate,
  event: PersistedEvent,
  candidateAllocations: List[CandidateAllocationPerSession],
  assessorAllocations: List[EventAssessorAllocationsSummaryPerSkill]
)

object EventWithAllocationsSummary {
  implicit val format = Json.format[EventWithAllocationsSummary]
}