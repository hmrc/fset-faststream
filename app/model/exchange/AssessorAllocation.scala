package model.exchange

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
}

case class AssessorAllocations(
  version: Option[String],
  allocations: Seq[AssessorAllocation]
)

object AssessorAllocations {
  implicit val assessorAllocationsFormat = Json.format[AssessorAllocations]

  def apply(o: Seq[model.persisted.AssessorAllocation]): AssessorAllocations = {
    val opLock = o.map(_.version).distinct match {
      case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions ${head ++ tail}")
      case head :: Nil => head
    }

    AssessorAllocations(Some(opLock), o.map { a => AssessorAllocation(a.id, a.status, a.allocatedAs) })
  }
}


case class CandidateAllocation(
  id: String,
  status: AllocationStatus
) extends Allocation

object CandidateAllocation {
  implicit val candidateAllocationFormat = Json.format[CandidateAllocation]
  implicit def fromPersisted(o: model.persisted.CandidateAllocation): CandidateAllocation = {
    CandidateAllocation(o.id, o.status)
  }
}

// TODO there must be a way to collapse these two case classes to a generic and infer the target type of the allocations member
case class CandidateAllocations(
  version: String,
  allocations: Seq[CandidateAllocation]
)

object CandidateAllocations {
  implicit val candidateAllocationsFormat = Json.format[CandidateAllocations]

  def apply(o: Seq[model.persisted.CandidateAllocation]): CandidateAllocations = {
    val opLock = o.map(_.version).distinct match {
      case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions ${head ++ tail}")
      case head :: Nil => head
    }
    CandidateAllocations(opLock, o.map { a => CandidateAllocation(a.id, a.status) })
  }
}
