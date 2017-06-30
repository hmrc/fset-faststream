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

  def apply(o: Seq[model.persisted.AssessorAllocation]): AssessorAllocations = {
    val (opLock, eventId) = o.map( a => a.version -> a.eventId).distinct match {
      case head :: tail => throw new Exception(s"Allocations to this event have mismatching op lock versions or event Ids ${head +: tail}")
      case head :: Nil => head
    }

    AssessorAllocations(opLock, eventId, o.map { a => AssessorAllocation(a.id, a.status, a.allocatedAs) })
  }

  def fromExchange(eventId: String, o: model.exchange.AssessorAllocations): AssessorAllocations = {
    AssessorAllocations(o.version.getOrElse(UUIDFactory.generateUUID()), eventId, o.allocations.map(AssessorAllocation.fromExchange))
  }
}
