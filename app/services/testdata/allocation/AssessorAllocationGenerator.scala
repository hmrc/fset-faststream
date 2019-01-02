/*
 * Copyright 2019 HM Revenue & Customs
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

import model.exchange.testdata.CreateAssessorAllocationResponse.CreateAssessorAllocationResponse
import model.testdata.CreateAssessorAllocationData.CreateAssessorAllocationData
import repositories.{ AssessorAllocationRepository, AssessorAllocationMongoRepository }

import scala.concurrent.Future

object AssessorAllocationGenerator extends AssessorAllocationGenerator {
  override val assessorAllocationRepository: AssessorAllocationMongoRepository = repositories.assessorAllocationRepository
}

trait AssessorAllocationGenerator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val assessorAllocationRepository: AssessorAllocationRepository

  def generate(generationId: Int, createData: CreateAssessorAllocationData): Future[CreateAssessorAllocationResponse] = {
    val assessorAllocation = createData.toAssessorAllocation
    assessorAllocationRepository.save(List(assessorAllocation)).map { _ => CreateAssessorAllocationResponse(generationId, createData) }
  }
}
