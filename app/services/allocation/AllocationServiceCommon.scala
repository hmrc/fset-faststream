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

package services.allocation

import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.exchange
import repositories.{ AssessorAllocationRepository, CandidateAllocationRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * This class is here to break circular dependencies between the EventsService and AssessorAllocationService
  * and CandidateAllocationService
  */
@ImplementedBy(classOf[AllocationServiceCommonImpl])
trait AllocationServiceCommon {
  def getAllocations(eventId: String): Future[exchange.AssessorAllocations]
  def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations]
}

@Singleton
class AllocationServiceCommonImpl @Inject() (assessorAllocationRepo: AssessorAllocationRepository,
                                             candidateAllocationRepo: CandidateAllocationRepository
                                            ) extends AllocationServiceCommon {

  override def getAllocations(eventId: String): Future[exchange.AssessorAllocations] = {
    assessorAllocationRepo.allocationsForEvent(eventId).map ( exchange.AssessorAllocations.apply )
  }

  override def getCandidateAllocations(eventId: String, sessionId: String): Future[exchange.CandidateAllocations] = {
    candidateAllocationRepo.activeAllocationsForSession(eventId, sessionId).map ( exchange.CandidateAllocations.apply )
  }
}
