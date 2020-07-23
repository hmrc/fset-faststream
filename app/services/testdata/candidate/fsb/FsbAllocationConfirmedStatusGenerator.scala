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

package services.testdata.candidate.fsb

import factories.UUIDFactory
import javax.inject.{ Inject, Singleton }
import model.command.testdata.CreateEventRequest
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.eventschedules.EventType
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{ AllocationStatuses, ProgressStatuses }
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import services.allocation.CandidateAllocationService
import services.application.FsbService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.event.EventGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FsbAllocationConfirmedStatusGenerator @Inject() (val previousStatusGenerator: FsbAwaitingAllocationStatusGenerator,
                                                       applicationRepository: GeneralApplicationRepository,
                                                       fsbTestGroupService: FsbService,
                                                       candidateAllocationService: CandidateAllocationService,
                                                       eventGenerator: EventGenerator,
                                                       schemeRepository: SchemeRepository,
                                                       uuidFactory: UUIDFactory
                                                      ) extends ConstructiveGenerator {

  def generate(generationId: Int, createCandidateData: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    val allSchemes = schemeRepository.schemes.toList

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, createCandidateData)
      appId = candidateInPreviousStatus.applicationId.get

      topSchemeId = candidateInPreviousStatus.schemePreferences.get.schemes.head
      fsbType = allSchemes.find(_.id == topSchemeId).flatMap(_.fsbType)
      event <- eventGenerator.createEvent(
        generationId,
        CreateEventRequest.random.copy(eventType = Some(EventType.FSB), description = fsbType.map(_.key))
      ).map(_.data)
      _ <- candidateAllocationService.allocateCandidates(
        CandidateAllocations(

          uuidFactory.generateUUID().toString,
          event.id,
          event.sessions.head.id,
          List(CandidateAllocation(appId, AllocationStatuses.CONFIRMED))
        ), append = false)
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_ALLOCATION_CONFIRMED)
    } yield {
      candidateInPreviousStatus
    }
  }
}
