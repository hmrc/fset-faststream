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

package model.command

import factories.UUIDFactory
import model.AllocationStatuses.AllocationStatus
import model.exchange.AssessorSkill
import play.api.libs.json.{ Json, OFormat }

trait Allocation {
  def id: String
  def status: AllocationStatus
}

case class AssessorAllocation(
  id: String,
  status: AllocationStatus,
  allocatedAs: AssessorSkill
) extends Allocation {
  override def toString =
    s"id=$id," +
      s"status=$status," +
      s"allocatedAs=$allocatedAs"
}

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
) {
  override def toString =
    s"[AssessorAllocations]:" +
      s"version=$version," +
      s"eventId=$eventId," +
      s"allocations=$allocations"
}

object AssessorAllocations {
  implicit val assessorAllocationsFormat = Json.format[AssessorAllocations]

  def apply(eventId: String, assessorAllocations: Seq[model.persisted.AssessorAllocation]): AssessorAllocations = {
    val opLock = assessorAllocations.map(_.version).distinct match {
      case head +: Nil => head
      case head +: tail =>
        val assessorIds = assessorAllocations.map(_.id).distinct
        throw new Exception(
          s"Allocations to this event [eventId=$eventId] and these assessors [assessorIds=$assessorIds]" +
            s" have mismatching op lock versions ${head +: tail}")
      case Nil => UUIDFactory.generateUUID() // TODO: the factory needs to be passed as an argument
    }

    AssessorAllocations(opLock, eventId, assessorAllocations.map { allocation =>
      val allocatedSkill = AssessorSkill.SkillMap(allocation.allocatedAs)
      AssessorAllocation(allocation.id, allocation.status, allocatedSkill)
    })
  }

  def fromExchange(eventId: String, o: model.exchange.AssessorAllocations): AssessorAllocations = {
    AssessorAllocations(o.version.getOrElse(UUIDFactory.generateUUID()), eventId, o.allocations.map(AssessorAllocation.fromExchange))
  }
}

case class CandidateAllocation(
  id: String,
  status: AllocationStatus
) extends Allocation {
  override def toString =
    s"id=$id," +
    s"status=$status"
}

object CandidateAllocation {
  implicit val candidateAllocationFormat: OFormat[CandidateAllocation] = Json.format[CandidateAllocation]

  def fromExchange(o: model.exchange.CandidateAllocation): CandidateAllocation = {
    CandidateAllocation(o.id, o.status)
  }

  def fromPersisted(o: model.persisted.CandidateAllocation): CandidateAllocation = {
    CandidateAllocation(o.id, o.status)
  }
}

case class CandidateAllocations(
  version: String,
  eventId: String,
  sessionId: String,
  allocations: Seq[CandidateAllocation]
) {
  override def toString =
    s"version=$version," +
    s"eventId=$eventId," +
    s"sessionId=$sessionId," +
    s"allocations=$allocations"
}

object CandidateAllocations {
  implicit val candidateAllocationsFormat: OFormat[CandidateAllocations] = Json.format[CandidateAllocations]

  def apply(eventId: String, sessionId: String, allocations: Seq[model.persisted.CandidateAllocation]): CandidateAllocations = {
    val opLock = allocations.map(_.version).distinct match {
      case head +: Nil => head
      case head +: tail =>
        val userIds = allocations.map(_.id).distinct
        throw new Exception(
          s"Allocations to this event [eventId=$eventId] and these users [userIds=$userIds] have mismatching op lock versions ${head +: tail}")
      case Nil => UUIDFactory.generateUUID()
    }

    CandidateAllocations(
      version = opLock,
      eventId = eventId,
      sessionId = sessionId,
      allocations = allocations.map { a => CandidateAllocation(a.id, a.status) }
    )
  }

  def fromExchange(eventId: String, sessionId: String, o: model.exchange.CandidateAllocations): CandidateAllocations = {
    CandidateAllocations(
      version = o.version.getOrElse(UUIDFactory.generateUUID()),
      eventId = eventId,
      sessionId = sessionId: String,
      allocations = o.allocations.map(CandidateAllocation.fromExchange)
    )
  }
}
