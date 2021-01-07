/*
 * Copyright 2021 HM Revenue & Customs
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

package model.testdata

import model.AllocationStatuses.AllocationStatus
import model.command.testdata.CreateAssessorAllocationRequest.CreateAssessorAllocationRequest
import model.persisted.AssessorAllocation
import model.persisted.eventschedules.SkillType
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker

case class CreateAssessorAllocationData(id: String,
                                        eventId: String,
                                        status: AllocationStatus,
                                        allocatedAs: String,
                                        version: String) extends CreateTestData {
  def toAssessorAllocation: AssessorAllocation = {
    AssessorAllocation(id, eventId, status, SkillType.withName(allocatedAs), version)
  }
}

object CreateAssessorAllocationData {
  implicit val format: OFormat[CreateAssessorAllocationData] = Json.format[CreateAssessorAllocationData]

  def apply(createRequest: CreateAssessorAllocationRequest, dataFaker: DataFaker)(generatorId: Int): CreateAssessorAllocationData = {
    val id = createRequest.id
    val eventId = createRequest.eventId
    val status = createRequest.status.getOrElse(dataFaker.Allocation.status)
    val allocatedAs = createRequest.allocatedAs
    val version = createRequest.version.getOrElse("")
    new CreateAssessorAllocationData(id, eventId, status, allocatedAs, version)
  }
}
