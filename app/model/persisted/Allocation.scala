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

import factories.UUIDFactory
import model.AllocationStatuses
import model.AllocationStatuses.AllocationStatus
import model.persisted.eventschedules.SkillType.SkillType
import play.api.libs.json.Json
import reactivemongo.bson.Macros

trait Allocation {
  def id: String
  def eventId: String
  def status: AllocationStatus
  def version: String
}

case class AssessorAllocation(
  id: String,
  eventId: String,
  status: AllocationStatus,
  allocatedAs: SkillType,
  version: String
) extends Allocation

object AssessorAllocation {
  implicit val assessorAllocationFormat = Json.format[AssessorAllocation]
  implicit val assessorAllocationHandler = Macros.handler[AssessorAllocation]

  def fromCommand(o: model.command.AssessorAllocations): Seq[AssessorAllocation] = {
    val opLockVerion = UUIDFactory.generateUUID()
    o.allocations.map { a => AssessorAllocation(a.id, o.eventId, AllocationStatuses.UNCONFIRMED, a.allocatedAs, opLockVerion) }
  }
}

case class CandidateAllocation(
  id: String,
  eventId: String,
  status: AllocationStatus,
  version: String
) extends Allocation

object CandidateAllocation {
  implicit val candidateAllocationFormat = Json.format[CandidateAllocation]
  implicit val candidateAllocationHandler = Macros.handler[CandidateAllocation]
}


