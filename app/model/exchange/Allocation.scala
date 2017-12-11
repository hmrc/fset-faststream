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

package model.exchange

import model.AllocationStatuses.AllocationStatus
import play.api.libs.json.{ Json, OFormat }

trait Allocation {
  def id: String
  def status: AllocationStatus
}

case class AssessorAllocation(
  id: String,
  status: AllocationStatus,
  allocatedAs: AssessorSkill
) extends Allocation

object AssessorAllocation {
  implicit val assessorAllocationFormat: OFormat[AssessorAllocation] = Json.format[AssessorAllocation]
}

case class AssessorAllocations(
  version: Option[String],
  allocations: Seq[AssessorAllocation]
)

object AssessorAllocations {
  implicit val assessorAllocationsFormat: OFormat[AssessorAllocations] = Json.format[AssessorAllocations]

  def apply(o: Seq[model.persisted.AssessorAllocation]): AssessorAllocations = {
      val opLock = o.map(_.version).distinct match {
        case head :: Nil => Some(head)
        case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions ${head ++ tail}")
        case Nil => None
      }

      AssessorAllocations(opLock, o.map { a =>
        val allocatedSkill = AssessorSkill.SkillMap(a.allocatedAs)
        AssessorAllocation(a.id, a.status, allocatedSkill)
      })
  }
}

case class CandidateAllocation(
  id: String,
  status: AllocationStatus,
  removeReason: Option[String]
) extends Allocation

object CandidateAllocation {
  implicit val candidateAllocationFormat: OFormat[CandidateAllocation] = Json.format[CandidateAllocation]
  def fromPersisted(o: model.persisted.CandidateAllocation): CandidateAllocation = {
    CandidateAllocation(o.id, o.status, o.removeReason)
  }
}

// TODO there must be a way to collapse these two case classes to a generic and infer the target type of the allocations member
case class CandidateAllocations(
  version: Option[String],
  allocations: Seq[CandidateAllocation]
)

object CandidateAllocations {
  implicit val candidateAllocationsFormat: OFormat[CandidateAllocations] = Json.format[CandidateAllocations]

  def apply(o: Seq[model.persisted.CandidateAllocation]): CandidateAllocations = {
    val opLock = o.map(_.version).distinct match {
      case head :: Nil => Some(head)
      case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions ${head ++ tail}")
      case Nil => None
    }
    CandidateAllocations(opLock, o.map { a => CandidateAllocation(a.id, a.status, a.removeReason) })
  }
}
