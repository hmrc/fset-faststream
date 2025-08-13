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

package services.allocation

import connectors.ExchangeObjects.Candidate
import connectors.{AuthProviderClient, OnlineTestEmailClient}
import model.Exceptions.OptimisticLockException
import model.exchange.AssessorSkill
import model.persisted.eventschedules.*
import model.persisted.eventschedules.SkillType.ASSESSOR
import model.{AllocationStatuses, command, persisted}
import org.mockito.ArgumentMatchers.{eq as eqTo, *}
import org.mockito.Mockito.{when, *}
import org.mockito.stubbing.OngoingStubbing
import repositories.AssessorAllocationRepository
import repositories.application.GeneralApplicationRepository
import services.BaseServiceSpec
import services.assessor.AssessorService
import services.events.EventsService
import services.stc.StcEventService
import testkit.ExtendedTimeout
import testkit.MockitoImplicits.*
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, LocalTime, OffsetDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AssessorAllocationServiceSpec extends BaseServiceSpec with ExtendedTimeout {

  "Allocate assessors" must {
    "save allocations if none already exist" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(Nil)
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      mockGetEvent
      mockAuthProviderFindByUserIds("userId1")

      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("userId1", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository, never()).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorAllocatedToEvent(
        any[String], any[String](), any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier], any[ExecutionContext])
    }

    "delete existing allocations and save new ones" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      )
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      mockGetEvent
      when(mockAssessorService.removeAvailabilities(any[Seq[String]], any[LocalDate])).thenReturnAsync()
      mockAuthProviderFindByUserIds("userId1", "userId2")

      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("userId2", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit

      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorUnAllocatedFromEvent(
        any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockEmailClient).sendAssessorAllocatedToEvent(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier], any[ExecutionContext])
    }

    "delete existing allocations and save none" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      )
      // This is what it will do if we want to save with an empty parameter in save(...). If we get the exception
      // it means we have reached this method and that's wrong
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenThrow(
        new java.lang.IllegalArgumentException("writes is not an empty list"))
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      mockGetEvent
      when(mockAssessorService.removeAvailabilities(any[Seq[String]], any[LocalDate])).thenReturnAsync()
      mockAuthProviderFindByUserIds("userId1", "userId2")

      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = Nil
      )
      val result = service.allocate(allocations).futureValue
      result mustBe unit

      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository, never()).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorUnAllocatedFromEvent(
        any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockEmailClient, never()).sendAssessorAllocatedToEvent(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier], any[ExecutionContext])
    }

    // We have an event with an assessor and a qac assigned and we remove the qac. This results in the qac availabilities
    // being removed. The availabilities for the assessor should not be deleted.
    "process an event with an assessor and a reviewer and the reviewer is removed" in new TestFixture {
      // These are the existing allocations that contain the assessor and the qac
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1") ::
        persisted.AssessorAllocation("userId2", "eventId1", AllocationStatuses.CONFIRMED, SkillType.QUALITY_ASSURANCE_COORDINATOR, "version1") :: Nil
      )
      mockGetEvent
      // We expect availabilities only for user2 (the qac) to be removed
      when(mockAssessorService.removeAvailabilities(eqTo(Seq("userId2")), any[LocalDate])).thenReturnAsync()
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      mockAuthProviderFindByUserIds("userId1", "userId2")

      // The new allocations do not include the reviewer who has been removed
      val newAllocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = Seq(command.AssessorAllocation("userId1", AllocationStatuses.CONFIRMED, AssessorSkill(ASSESSOR, "FSAC Assessor")))
      )
      val result = service.allocate(newAllocations).futureValue
      result mustBe unit

      verify(mockAssessorService).removeAvailabilities(any[Seq[String]], any[LocalDate])
      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorUnAllocatedFromEvent(
        any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockEmailClient, never()).sendAssessorAllocatedToEvent(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier], any[ExecutionContext])
    }

    "change an assessor's role in a new allocation" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      )
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturnAsync()
      mockGetEvent
      when(mockAssessorService.removeAvailabilities(any[Seq[String]], any[LocalDate])).thenReturnAsync()
      mockAuthProviderFindByUserIds("userId1")

      val allocations = command.AssessorAllocations(
        version = "version1",
        eventId = "eventId1",
        allocations = command.AssessorAllocation("userId1", AllocationStatuses.CONFIRMED,
          allocatedAs = AssessorSkill(SkillType.ASSESSOR, "Assessor")) :: Nil
      )
      val result = service.allocate(allocations).futureValue

      result mustBe unit

      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorEventAllocationChanged(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier], any[ExecutionContext])
    }

    "throw an optimistic lock exception if data has changed before saving" in new TestFixture {
       when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturnAsync(
        persisted.AssessorAllocation("id", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version5") :: Nil
      )
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

  trait TestFixture {
    val mockAllocationRepository = mock[AssessorAllocationRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockEventsService = mock[EventsService]
    val mockAllocationServiceCommon = mock[AllocationServiceCommon]
    val mockAssessorService = mock[AssessorService]
    val mockStcEventService = mock[StcEventService]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val mockEmailClient = mock[OnlineTestEmailClient] //TODO:fix change type

    val service = new AssessorAllocationService(
      mockAllocationRepository,
      mockAppRepo,
      mockEventsService,
      mockAllocationServiceCommon,
      mockAssessorService,
      mockStcEventService,
      mockAuthProviderClient,
      mockEmailClient
    )

    protected def mockGetEvent: OngoingStubbing[Future[Event]] = when(mockEventsService.getEvent(any[String]())).thenReturnAsync(Event(
      "eventId", EventType.FSAC, "Description", Location("London"), Venue("Venue 1", "venue description"),
      LocalDate.now, 10, 10, 10, LocalTime.now, LocalTime.now, OffsetDateTime.now, Map(), Nil
    ))

    protected def mockAuthProviderFindByUserIds(userId: String*): Unit = userId.foreach { uid =>
      when(mockAuthProviderClient.findByUserIds(eqTo(Seq(uid)))(any[HeaderCarrier]())).thenReturnAsync(
        Seq(
          Candidate("Bob " + uid, "Smith", preferredName = None, "bob@mailinator.com", phone = None, userId = uid, List("candidate"))
        )
      )
    }
  }
}
