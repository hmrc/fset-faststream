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

package services.allocation

import connectors.{ AuthProviderClient, EmailClient }
import model.Exceptions.OptimisticLockException
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.exchange.AssessorSkill
import model.persisted.EventExamples
import model.persisted.eventschedules.SkillType
import model.{ AllocationStatuses, command, persisted }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.{ AssessorAllocationMongoRepository, CandidateAllocationMongoRepository }
import services.BaseServiceSpec
import services.events.EventsService
import services.stc.StcEventService

import scala.concurrent.Future

class AssessorAllocationServiceSpec extends BaseServiceSpec {

  "Allocate assessors" must {
    "save allocations if none already exist" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(Nil))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("id", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository, times(0)).delete(any[Seq[persisted.AssessorAllocation]])
    }

    "delete existing allocations and save new ones" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(
        persisted.AssessorAllocation("id", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      ))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("id", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit
      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
    }

    "throw an optimistic lock exception if data has changed before saving" in new TestFixture {
       when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(
        persisted.AssessorAllocation("id", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version5") :: Nil
      ))
      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("id", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).failed.futureValue
      result mustBe an[OptimisticLockException]
    }
  }

  "Allocate candidate" must {
    "save allocation if non already exists" in new TestFixture {
      val eventId = "E1"
      val appId = "app1"
      val candidateAllocations = CandidateAllocations("v1", eventId, Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))

      when(mockEventsService.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1))
      when(mockCandidateAllocationRepository.allocationsForEvent(eventId)).thenReturn(Future.successful(Nil))
      when(mockAppRepo.find(appId)).thenReturn(Future.successful(None))
      service.allocateCandidates(candidateAllocations)
    }
  }

  trait TestFixture {
    val mockAllocationRepository = mock[AssessorAllocationMongoRepository]
    val mockCandidateAllocationRepository = mock[CandidateAllocationMongoRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockEventsService = mock[EventsService]
    val mockEmailClient = mock[EmailClient]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val mockStcEventService = mock[StcEventService]
    val service = new AssessorAllocationService {
      def assessorAllocationRepo: AssessorAllocationMongoRepository = mockAllocationRepository
      def candidateAllocationRepo: CandidateAllocationMongoRepository = mockCandidateAllocationRepository
      override val eventsService: EventsService = mockEventsService
      override val applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def emailClient: EmailClient = mockEmailClient
      override def authProviderClient: AuthProviderClient = mockAuthProviderClient
      override val eventService: StcEventService = mockStcEventService
    }
  }

}
