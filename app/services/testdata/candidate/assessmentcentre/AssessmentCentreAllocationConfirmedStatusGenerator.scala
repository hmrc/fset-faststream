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

package services.testdata.candidate.assessmentcentre

import factories.UUIDFactory
import model.command.testdata.CreateEventRequest.CreateEventRequest
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.eventschedules.EventType
import model.testdata.CreateCandidateData.CreateCandidateData
import model.{ AllocationStatuses, ProgressStatuses }
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.allocation.CandidateAllocationService
import services.testdata.candidate.{ BaseGenerator, ConstructiveGenerator }
import services.testdata.event.EventGenerator
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AssessmentCentreAllocationConfirmedStatusGenerator extends AssessmentCentreAllocationConfirmedStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreAwaitingAllocationStatusGenerator
  override val applicationRepository = repositories.applicationRepository
  override val candidateAllocationService = CandidateAllocationService
  override val eventGenerator = EventGenerator
}

trait AssessmentCentreAllocationConfirmedStatusGenerator extends ConstructiveGenerator {
  val applicationRepository: GeneralApplicationRepository
  val candidateAllocationService: CandidateAllocationService
  val eventGenerator: EventGenerator

  def generate(generationId: Int, createCandidateData: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, createCandidateData)
      applicationId = candidateInPreviousStatus.applicationId.get
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED)
      // we're generating random event here, and assigning candidate to it.
      event <- eventGenerator.createEvent(generationId, CreateEventRequest.random.copy(eventType = Some(EventType.FSAC))).map(_.data)
      _ <- candidateAllocationService.allocateCandidates(
        CandidateAllocations(
          UUIDFactory.generateUUID().toString,
          event.id,
          event.sessions.head.id,
          List(CandidateAllocation(applicationId, AllocationStatuses.CONFIRMED))
        ), append = false)

    } yield {
      candidateInPreviousStatus
    }
  }
}
