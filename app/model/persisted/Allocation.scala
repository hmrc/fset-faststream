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
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.Macros

trait Allocation {
  def eventId: String
  def status: AllocationStatus
  def version: String
  def userQueryKey: String
  def userQueryValue: String
}

case class AssessorAllocation(
                               userId: String,
                               eventId: String,
                               status: AllocationStatus,
                               allocatedAs: SkillType,
                               version: String
) extends Allocation {
  override val userQueryKey = "userId"
  override val userQueryValue = userId
}

object AssessorAllocation {
  implicit val assessorAllocationFormat: OFormat[AssessorAllocation] = Json.format[AssessorAllocation]
  implicit val assessorAllocationHandler = Macros.handler[AssessorAllocation]

  def fromCommand(o: model.command.AssessorAllocations, opLockVersion: String = UUIDFactory.generateUUID()): Seq[AssessorAllocation] = {
    o.allocations.map { a => AssessorAllocation(a.userId, o.eventId, a.status, a.allocatedAs.name, opLockVersion) }
  }
}

case class CandidateAllocation(
                                applicationId: String,
                                eventId: String,
                                sessionId: String,
                                status: AllocationStatus,
                                version: String
) extends Allocation {
  override val userQueryKey = "applicationId"
  override val userQueryValue = applicationId
}

object CandidateAllocation {
  implicit val candidateAllocationFormat: OFormat[CandidateAllocation] = Json.format[CandidateAllocation]
  implicit val candidateAllocationHandler = Macros.handler[CandidateAllocation]

  def fromCommand(allocations: model.command.CandidateAllocations): Seq[CandidateAllocation] = {
    val opLockVersion = UUIDFactory.generateUUID()
    allocations.allocations.map { allocation =>
      CandidateAllocation(
        applicationId = allocation.applicationId,
        eventId = allocations.eventId,
        sessionId = allocations.sessionId,
        status = allocation.status,
        version = opLockVersion
      )
    }
  }

  def fromExchange(o: model.exchange.CandidateAllocations, eventId: String, sessionId: String) : Seq[CandidateAllocation] = {
    o.allocations.map { a =>
      CandidateAllocation(
        applicationId = a.id,
        eventId = eventId,
        sessionId = sessionId,
        status = a.status,
        version = o.version.getOrElse(UUIDFactory.generateUUID())
      )
    }
  }
}
