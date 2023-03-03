/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.command.testdata.CreateEventRequest
import model.command.{CandidateAllocation, CandidateAllocations}
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.eventschedules.EventType
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{AllocationStatuses, ProgressStatuses}
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.allocation.CandidateAllocationService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.event.EventGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

//scalastyle:off line.size.limit
@Singleton
class AssessmentCentreAllocationConfirmedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreAwaitingAllocationStatusGenerator,
                                                                    applicationRepository: GeneralApplicationRepository,
                                                                    candidateAllocationService: CandidateAllocationService,
                                                                    eventGenerator: EventGenerator,
                                                                    uuidFactory: UUIDFactory
                                                                   )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  def generate(generationId: Int, createCandidateData: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec : ExecutionContext): Future[CreateCandidateResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, createCandidateData)
      applicationId = candidateInPreviousStatus.applicationId.get
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED)
      // we're generating random event here, and assigning candidate to it.
      event <- eventGenerator.createEvent(generationId, CreateEventRequest.random.copy(eventType = Some(EventType.FSAC))).map(_.data)
      _ <- candidateAllocationService.allocateCandidates(
        CandidateAllocations(
          uuidFactory.generateUUID().toString,
          event.id,
          event.sessions.head.id,
          List(CandidateAllocation(applicationId, AllocationStatuses.CONFIRMED))
        ), append = false)

    } yield {
      candidateInPreviousStatus
    }
  }
} //scalastyle:on
