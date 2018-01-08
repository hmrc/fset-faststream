/*
 * Copyright 2018 HM Revenue & Customs
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
import model.{ AllocationStatuses, ProgressStatuses }
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.command.testdata.CreateEventRequest.CreateEventRequest
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.eventschedules.EventType
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.SchemeYamlRepository
import repositories.application.GeneralApplicationRepository
import services.allocation.CandidateAllocationService
import services.application.FsbService
import services.testdata.candidate.{ BaseGenerator, ConstructiveGenerator }
import services.testdata.event.EventGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object FsbAllocationConfirmedStatusGenerator extends FsbAllocationConfirmedStatusGenerator {
  override val previousStatusGenerator: BaseGenerator = FsbAwaitingAllocationStatusGenerator
  override val applicationRepository = repositories.applicationRepository
  override val candidateAllocationService = CandidateAllocationService
  override val fsbTestGroupService = FsbService
  override val eventGenerator = EventGenerator
}

trait FsbAllocationConfirmedStatusGenerator extends ConstructiveGenerator {
  val applicationRepository: GeneralApplicationRepository
  val fsbTestGroupService: FsbService
  val candidateAllocationService: CandidateAllocationService
  val eventGenerator: EventGenerator

  def generate(generationId: Int, createCandidateData: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    val allSchemes = SchemeYamlRepository.schemes.toList

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
          UUIDFactory.generateUUID().toString,
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
