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

package services.allocation

import config.EventsConfig
import connectors.{ AuthProviderClient, EmailClient }
import connectors.ExchangeObjects.Candidate
import model.{ AllocationStatuses, CandidateExamples, persisted }
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.exchange.candidateevents.CandidateAllocationWithEvent
import model.exchange.{ CandidateEligibleForEvent, CandidatesEligibleForEventResponse }
import model.persisted._
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, EventType, Location, Venue }
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ when, _ }
import org.mockito.stubbing.OngoingStubbing
import repositories.{ CandidateAllocationMongoRepository, SchemeRepository, SchemeYamlRepository }
import repositories.application.GeneralApplicationRepository
import services.BaseServiceSpec
import services.events.EventsService
import services.stc.StcEventService
import testkit.MockitoImplicits._
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class CandidateAllocationServiceSpec extends BaseServiceSpec {
  "Allocate candidate" must {
    "save allocation if non already exists" in new TestFixture {
      val eventId = "E1"
      val sessionId = "S1"
      val appId = "app1"
      val candidateAllocations = CandidateAllocations("v1", eventId, sessionId, Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(Nil)
      when(mockAppRepo.find(appId)).thenReturnAsync(None)
      service.allocateCandidates(candidateAllocations, append = false)
    }
  }

  "Unallocate candidate" must {
    "unallocate candidates" in new TestFixture {
      val eventId = "E1"
      val sessionId = "S1"
      val appId = "app1"
      val userId = "userId"
      val candidateAllocations = CandidateAllocations("v1", eventId, sessionId, Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))
      val persistedAllocations: Seq[persisted.CandidateAllocation] = model.persisted.CandidateAllocation.fromCommand(candidateAllocations)
      val allocation: persisted.CandidateAllocation = persistedAllocations.head

      when(mockCandidateAllocationRepository.isAllocationExists(any[String], any[String], any[String], any[Option[String]]))
        .thenReturnAsync(true)
      when(mockCandidateAllocationRepository.removeCandidateAllocation(any[persisted.CandidateAllocation])).thenReturnAsync()
      when(mockAppRepo.resetApplicationAllocationStatus(any[String], any[EventType])).thenReturnAsync()

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockAppRepo.find(List(appId))).thenReturnAsync(CandidateExamples.NewCandidates)
      when(mockPersonalDetailsRepo.find(any[String])).thenReturnAsync(PersonalDetailsExamples.JohnDoe)
      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(ContactDetailsExamples.ContactDetailsUK)

      when(mockEmailClient.sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturnAsync()

      service.unAllocateCandidates(persistedAllocations.toList).futureValue

      verify(mockCandidateAllocationRepository).removeCandidateAllocation(any[model.persisted.CandidateAllocation])
      verify(mockAppRepo).resetApplicationAllocationStatus(any[String], any[EventType])
      verify(mockEmailClient).sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier])
    }
  }

  "find eligible candidates" must {
    "return all candidates except no-shows" in new TestFixture {

      private val fsacIndicator = model.FSACIndicator("","")
      private val c1 = CandidateEligibleForEvent("app1", "", "", needsAdjustment = true, fsacIndicator, DateTime.now())
      private val c2 = CandidateEligibleForEvent("app2", "", "", needsAdjustment = true, fsacIndicator, DateTime.now())
      private val loc = "London"
      private val eventType = EventType.FSAC
      private val desc = "ORAC"
      private val scheme = None

      val res = CandidatesEligibleForEventResponse(List(c1, c2), 2)
      when(mockAppRepo.findCandidatesEligibleForEventAllocation(List(loc), eventType, scheme)).thenReturnAsync(res)

      service.findCandidatesEligibleForEventAllocation(loc, eventType, desc).futureValue mustBe res
    }
  }

  "get sessions for application" must {
    "get list of events with sessions only that the application is a part of" in new TestFixture {
      when(mockCandidateAllocationRepository.allocationsForApplication(any[String]())).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "appId1", EventExamples.e1.id, EventExamples.e1Session1Id, AllocationStatuses.UNCONFIRMED,
            "version1", None, LocalDate.now(), reminderSent = false
          )
        )
      )

      when(mockEventsService.getEvents(any[List[String]]())).thenReturnAsync(
        List(EventExamples.e1WithSessions)
      )

      service.getSessionsForApplication("appId1").futureValue mustBe List(
        CandidateAllocationWithEvent("appId1", "version1", AllocationStatuses.UNCONFIRMED,
          model.exchange.Event(
            EventExamples.e1WithSessions.copy(sessions = EventExamples.e1WithSessions.sessions.filter(_.id == EventExamples.e1Session1Id))
          )
        )
      )

    }
  }


  trait TestFixture {
    val mockCandidateAllocationRepository: CandidateAllocationMongoRepository = mock[CandidateAllocationMongoRepository]
    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockPersonalDetailsRepo: PersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockContactDetailsRepo: ContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEventsService: EventsService = mock[EventsService]
    val mockEmailClient: EmailClient = mock[EmailClient]
    val mockAuthProviderClient: AuthProviderClient = mock[AuthProviderClient]
    val mockStcEventService: StcEventService = mock[StcEventService]

    val service = new CandidateAllocationService {
      override val eventsService: EventsService = mockEventsService
      override val applicationRepo: GeneralApplicationRepository = mockAppRepo
      override val personalDetailsRepo: PersonalDetailsRepository = mockPersonalDetailsRepo
      override val contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo

      override def emailClient: EmailClient = mockEmailClient

      override def authProviderClient: AuthProviderClient = mockAuthProviderClient

      override val eventService: StcEventService = mockStcEventService

      def candidateAllocationRepo: CandidateAllocationMongoRepository = mockCandidateAllocationRepository

      override def schemeRepository: SchemeRepository = SchemeYamlRepository

      override def eventsConfig: EventsConfig = EventsConfig("", "", 1)
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
