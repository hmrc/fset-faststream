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

package model.testdata

import model.AllocationStatuses.AllocationStatus
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.command.testdata.CreateCandidateAllocationRequest
import play.api.libs.json.{ Json, OFormat }
import services.testdata.faker.DataFaker

case class CreateCandidateAllocationData(id: String,
                                         eventId: String,
                                         sessionId: String,
                                         status: AllocationStatus,
                                         version: String) extends CreateTestData {
  def toCandidateAllocations: CandidateAllocations = {
    CandidateAllocations(version, eventId, sessionId, Seq(CandidateAllocation(id, status)))
  }
}

object CreateCandidateAllocationData {
  implicit val format: OFormat[CreateCandidateAllocationData] = Json.format[CreateCandidateAllocationData]

  def apply(createRequest: CreateCandidateAllocationRequest, dataFaker: DataFaker)(generatorId: Int): CreateCandidateAllocationData = {
    val id = createRequest.id
    val eventId = createRequest.eventId
    val sessionId = createRequest.sessionId
    val status = createRequest.status.getOrElse(dataFaker.Allocation.status)
    val version = createRequest.version.getOrElse("")
    new CreateCandidateAllocationData(id, eventId, sessionId, status, version)
  }
}
