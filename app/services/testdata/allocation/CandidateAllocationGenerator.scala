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

package services.testdata.allocation

import javax.inject.{ Inject, Singleton }
import model.exchange.testdata.CreateCandidateAllocationResponse
import model.testdata.CreateCandidateAllocationData
import play.api.mvc.RequestHeader
import services.allocation.CandidateAllocationService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class CandidateAllocationGenerator @Inject() (candidateAllocationService: CandidateAllocationService) {

  def generate(generationId: Int, createData: CreateCandidateAllocationData)(
    implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateAllocationResponse] = {

    candidateAllocationService.getCandidateAllocations(createData.eventId, createData.sessionId).flatMap { existingAllocation =>

      val newAllocations = createData.toCandidateAllocations

      val newAllocationsWithVersion = if(newAllocations.version.isEmpty) {
        newAllocations.copy(version = existingAllocation.version.getOrElse(""))
      } else {
        createData.toCandidateAllocations
      }

      candidateAllocationService.allocateCandidates(newAllocationsWithVersion, append = true).map { d =>
        CreateCandidateAllocationResponse(generationId, createData.copy(version = d.version))
      }
    }
  }
}
