package model.exchange

import model.AllocationStatuses.AllocationStatus
import play.api.libs.json.{ Json, OFormat }

import scala.collection.GenTraversableOnce
import scala.util.{ Failure, Success, Try }

case class UpdateAssessorAllocationStatus(
  assessorId: String,
  eventId: String,
  newStatus: AllocationStatus
)

object UpdateAssessorAllocationStatus {
  implicit val updateAssessorAllocationStatusFormat: OFormat[UpdateAssessorAllocationStatus] = Json.format[UpdateAssessorAllocationStatus]
}



