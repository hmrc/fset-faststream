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

package model.command

import factories.UUIDFactory
import model.AllocationStatuses.AllocationStatus
import model.persisted.eventschedules.SkillType.SkillType
import play.api.libs.json.Json

trait Allocation {
  def id: String
  def status: AllocationStatus
}

case class AssessorAllocation(
  id: String,
  status: AllocationStatus,
  allocatedAs: SkillType
) extends Allocation

object AssessorAllocation {
  implicit val assessorAllocationFormat = Json.format[AssessorAllocation]

  def fromExchange(o: model.exchange.AssessorAllocation): AssessorAllocation = {
    AssessorAllocation(o.id, o.status, o.allocatedAs)
  }
}

case class AssessorAllocations(
  version: String,
  eventId: String,
  allocations: Seq[AssessorAllocation]
)

object AssessorAllocations {
  implicit val assessorAllocationsFormat = Json.format[AssessorAllocations]

  def apply(eventId: String, o: Seq[model.persisted.AssessorAllocation]): AssessorAllocations = {
    val opLock = o.map(_.version).distinct match {
      case head :: Nil => head
      case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions ${head +: tail}")
      case Nil => UUIDFactory.generateUUID()
    }

    AssessorAllocations(opLock, eventId, o.map { a => AssessorAllocation(a.id, a.status, a.allocatedAs) })
  }

  def fromExchange(eventId: String, o: model.exchange.AssessorAllocations): AssessorAllocations = {
    AssessorAllocations(o.version.getOrElse(UUIDFactory.generateUUID()), eventId, o.allocations.map(AssessorAllocation.fromExchange))
  }
}
