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

package services.allocation

import connectors.ExchangeObjects.Candidate
import connectors.{ AuthProviderClient, EmailClient }
import model.Exceptions.OptimisticLockException
import model.exchange.AssessorSkill
import model.persisted.eventschedules._
import model.{ AllocationStatuses, command, persisted }
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito.{ when, _ }
import org.mockito.stubbing.OngoingStubbing
import repositories.application.GeneralApplicationRepository
import repositories.AssessorAllocationMongoRepository
import services.BaseServiceSpec
import services.events.EventsService
import services.stc.StcEventService
import testkit.ExtendedTimeout

import scala.concurrent.Future
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

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
      verify(mockAllocationRepository, times(0)).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorAllocatedToEvent(
        any[String], any[String](), any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier])
    }

    "delete existing allocations and save new ones" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      ))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      mockGetEvent
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
        any[String], any[String], any[String])(any[HeaderCarrier])
      verify(mockEmailClient).sendAssessorAllocatedToEvent(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier])
    }

    "change an assessor's role in a new allocation" in new TestFixture {
      when(mockAllocationRepository.allocationsForEvent(any[String])).thenReturn(Future.successful(
        persisted.AssessorAllocation("userId1", "eventId1", AllocationStatuses.CONFIRMED, SkillType.CHAIR, "version1") :: Nil
      ))
      when(mockAllocationRepository.save(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
      when(mockAllocationRepository.delete(any[Seq[persisted.AssessorAllocation]])).thenReturn(Future.successful(unit))
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

      verify(mockAllocationRepository).delete(any[Seq[persisted.AssessorAllocation]])
      verify(mockAllocationRepository).save(any[Seq[persisted.AssessorAllocation]])
      verify(mockEmailClient).sendAssessorEventAllocationChanged(
        any[String], any[String], any[String], any[String], any[String], any[String], any[String]
      )(any[HeaderCarrier])
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

  trait TestFixture {
    val mockAllocationRepository: AssessorAllocationMongoRepository = mock[AssessorAllocationMongoRepository]
    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockEventsService: EventsService = mock[EventsService]
    val mockEmailClient: EmailClient = mock[EmailClient]
    val mockAuthProviderClient: AuthProviderClient = mock[AuthProviderClient]
    val mockStcEventService: StcEventService = mock[StcEventService]
    val service = new AssessorAllocationService {
      def assessorAllocationRepo: AssessorAllocationMongoRepository = mockAllocationRepository
      override val eventsService: EventsService = mockEventsService
      override val applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def emailClient: EmailClient = mockEmailClient
      override def authProviderClient: AuthProviderClient = mockAuthProviderClient
      override val eventService: StcEventService = mockStcEventService
    }

    protected def mockGetEvent: OngoingStubbing[Future[Event]] = when(mockEventsService.getEvent(any[String]())).thenReturnAsync(new Event(
      "eventId", EventType.FSAC, "Description", Location("London"), Venue("Venue 1", "venue description"),
      LocalDate.now, 10, 10, 10, LocalTime.now, LocalTime.now, DateTime.now, Map(), Nil
    ))

    protected def mockAuthProviderFindByUserIds(userId: String*): Unit = userId.foreach { uid =>
      when(mockAuthProviderClient.findByUserIds(eqTo(Seq(uid)))(any[HeaderCarrier]())).thenReturnAsync(
        Seq(
          Candidate("Bob " + uid, "Smith", None, "bob@mailinator.com", None, uid, List("candidate"))
        )
      )
    }
  }
}
