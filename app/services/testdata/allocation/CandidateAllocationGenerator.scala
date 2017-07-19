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

package services.testdata.allocation

import model.exchange.testdata.CreateCandidateAllocationResponse
import model.persisted.CandidateAllocation
import model.testdata.CreateCandidateAllocationData
import repositories.AllocationRepository

import scala.concurrent.Future

object CandidateAllocationGenerator extends CandidateAllocationGenerator {
  override val candidateAllocationRepository: AllocationRepository[CandidateAllocation] = repositories.candidateAllocationRepository
}

trait CandidateAllocationGenerator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val candidateAllocationRepository: AllocationRepository[CandidateAllocation]

  def generate(generationId: Int, createData: CreateCandidateAllocationData): Future[CreateCandidateAllocationResponse] = {
    val allocation = createData.toCandidateAllocation
    candidateAllocationRepository.save(List(allocation)).map { _ => CreateCandidateAllocationResponse(generationId, createData) }
  }

}
