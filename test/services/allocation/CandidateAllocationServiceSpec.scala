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

import config.{EventsConfig, MicroserviceAppConfig}
import connectors.ExchangeObjects.Candidate
import connectors.{AuthProviderClient, OnlineTestEmailClient}
import model.ApplicationStatus.ApplicationStatus
import model._
import model.command.{CandidateAllocation, CandidateAllocations}
import model.exchange.candidateevents.CandidateAllocationWithEvent
import model.exchange.{CandidateEligibleForEvent, CandidatesEligibleForEventResponse}
import model.persisted._
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{Event, EventType, Location, Venue}
import org.joda.time.{DateTime, LocalDate, LocalTime}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{when, _}
import org.mockito.stubbing.OngoingStubbing
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{CandidateAllocationMongoRepository, SchemeRepository}
import services.BaseServiceSpec
import services.events.EventsService
import services.stc.{StcEventService, StcEventServiceFixture}
import testkit.ExtendedTimeout
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CandidateAllocationServiceSpec extends BaseServiceSpec with ExtendedTimeout{
  "Allocate candidate" must {
    "save allocation if none already exists" in new TestFixture {
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId = "appId1"
      val version = "v1"
      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      //here
//      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(Nil)
      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(
        exchange.CandidateAllocations(version = None, allocations = Nil)
      )

      when(mockCandidateAllocationRepository.save(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()
      when(mockAppRepo.removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])).thenReturnAsync()
      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val newAllocatedCandidate = allocatedCandidate(appId, "userId1", ApplicationStatus.FSB)
      when(mockAppRepo.find(appId)).thenReturnAsync(Some(newAllocatedCandidate))
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)


      val result = service.allocateCandidates(candidateAllocations, append = false).futureValue
      result.eventId mustBe eventId
      result.sessionId mustBe sessionId
      result.allocations mustBe Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED))
    }

    "exclude candidates who have been offered a job when processing new allocations for the same event" in new TestFixture {
      val version = "v1"
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId1 = "appId1"
      val appId2 = "appId2"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)

      val candidate1 = model.persisted.CandidateAllocation(
        id = appId1,
        eventId = eventId,
        sessionId = sessionId,
        status = AllocationStatuses.CONFIRMED,
        version = version,
        removeReason = None,
        createdAt = LocalDate.now(),
        reminderSent = false
      )

      val existingPersistedAllocations = Seq(candidate1)
      val existingAllocations = exchange.CandidateAllocations.apply(existingPersistedAllocations)
      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(existingAllocations)
      //here
//      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(existingAllocations)

      when(mockCandidateAllocationRepository.delete(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()
      when(mockCandidateAllocationRepository.save(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()

      when(mockAppRepo.getApplicationStatusForCandidates(Seq(appId2))).thenReturnAsync(Seq(appId2 -> ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER))

      val newAllocatedCandidate = allocatedCandidate(appId2, "userId2", ApplicationStatus.FSB)
      when(mockAppRepo.find(appId2)).thenReturnAsync(Some(newAllocatedCandidate))
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation(appId2, AllocationStatuses.UNCONFIRMED)))
      service.allocateCandidates(candidateAllocations, append = false).futureValue
      // Should not be called for the existing candidate who has been offered a job so should never be called in this scenario
      verify(mockAppRepo, never()).removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])
    }

    "include candidates who are still in FSB when processing new allocations for the same event" in new TestFixture {
      val version = "v1"
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId1 = "appId1"
      val appId2 = "appId2"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)

      val candidate1 = model.persisted.CandidateAllocation(
        id = appId1,
        eventId = eventId,
        sessionId = sessionId,
        status = AllocationStatuses.CONFIRMED,
        version = version,
        removeReason = None,
        createdAt = LocalDate.now(),
        reminderSent = false
      )

      val existingPersistedAllocations = Seq(candidate1)

      //here
//      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(existingPersistedAllocations)
      val existingAllocations = exchange.CandidateAllocations.apply(existingPersistedAllocations)
      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(existingAllocations)

      when(mockCandidateAllocationRepository.delete(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()
      when(mockCandidateAllocationRepository.save(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()

      when(mockAppRepo.getApplicationStatusForCandidates(Seq(appId2))).thenReturnAsync(Seq(appId2 -> ApplicationStatus.FSB))
      when(mockAppRepo.removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])).thenReturnAsync()
      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val newAllocatedCandidate = allocatedCandidate(appId2, "userId2", ApplicationStatus.FSB)
      when(mockAppRepo.find(appId2)).thenReturnAsync(Some(newAllocatedCandidate))
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation(appId2, AllocationStatuses.UNCONFIRMED)))

      service.allocateCandidates(candidateAllocations, append = false).futureValue

      // Should be called for the existing candidate who is still in FSB
      verify(mockAppRepo, times(1)).removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])
    }

    "include candidates who are in ASSESSMENT_CENTRE when processing new allocations for the same event" in new TestFixture {
      val version = "v1"
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId1 = "appId1"
      val appId2 = "appId2"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)

      val candidate1 = model.persisted.CandidateAllocation(
        id = appId1,
        eventId = eventId,
        sessionId = sessionId,
        status = AllocationStatuses.CONFIRMED,
        version = version,
        removeReason = None,
        createdAt = LocalDate.now(),
        reminderSent = false
      )

      val existingPersistedAllocations = Seq(candidate1)
//      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(existingPersistedAllocations)
      val existingAllocations = exchange.CandidateAllocations.apply(existingPersistedAllocations)
      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(existingAllocations)

      when(mockCandidateAllocationRepository.delete(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()
      when(mockCandidateAllocationRepository.save(any[Seq[model.persisted.CandidateAllocation]])).thenReturnAsync()

      when(mockAppRepo.getApplicationStatusForCandidates(Seq(appId2))).thenReturnAsync(Seq(appId2 -> ApplicationStatus.ASSESSMENT_CENTRE))
      when(mockAppRepo.removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])).thenReturnAsync()
      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      val newAllocatedCandidate = allocatedCandidate(appId2, "userId2", ApplicationStatus.ASSESSMENT_CENTRE)
      when(mockAppRepo.find(appId2)).thenReturnAsync(Some(newAllocatedCandidate))
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation(appId2, AllocationStatuses.UNCONFIRMED)))
      service.allocateCandidates(candidateAllocations, append = false).futureValue
      // Should be called for the existing candidate who is still in ASSESSMENT_CENTRE
      verify(mockAppRepo, times(1)).removeProgressStatuses(any[String], any[List[ProgressStatuses.ProgressStatus]])
    }
  }

  "Unallocate candidate" must {
    "unallocate candidate who is eligible for reallocation" in new TestFixture {
      commonUnallocateCandidates(eligibleForReallocation = true)
      verify(mockCandidateAllocationRepository).removeCandidateAllocation(any[model.persisted.CandidateAllocation])
      verify(mockAppRepo).resetApplicationAllocationStatus(any[String], any[EventType])
      verify(mockEmailClient).sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
    }

    "unallocate candidate who is not eligible for reallocation" in new TestFixture {
      commonUnallocateCandidates(eligibleForReallocation = false)

      verify(mockCandidateAllocationRepository).removeCandidateAllocation(any[model.persisted.CandidateAllocation])
      verify(mockAppRepo, never()).resetApplicationAllocationStatus(any[String], any[EventType])
      verify(mockEmailClient).sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  "find eligible candidates" must {
    "return all candidates except no-shows" in new TestFixture {
      private val fsacIndicator = model.FSACIndicator("","")
      private val c1 = CandidateEligibleForEvent("app1", "", "", needsAdjustment = true, fsbScoresAndFeedbackSubmitted = false,
        fsacIndicator, DateTime.now())
      private val c2 = CandidateEligibleForEvent("app2", "", "", needsAdjustment = true, fsbScoresAndFeedbackSubmitted = false,
        fsacIndicator, DateTime.now())
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

  trait TestFixture extends StcEventServiceFixture {
    val mockCandidateAllocationRepository = mock[CandidateAllocationMongoRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockPersonalDetailsRepo = mock[PersonalDetailsRepository]
    val mockSchemeRepository = mock[SchemeRepository]
    val mockEventsService = mock[EventsService]
    val mockAllocationServiceCommon = mock[AllocationServiceCommon]

    val mockStcEventService: StcEventService = stcEventServiceMock
    val mockEmailClient = mock[OnlineTestEmailClient]
    val mockAuthProviderClient: AuthProviderClient = mock[AuthProviderClient]
    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]

    when(mockMicroserviceAppConfig.eventsConfig).thenReturn(
      EventsConfig(scheduleFilePath = "", fsacGuideUrl = "", daysBeforeInvitationReminder = 1, maxNumberOfCandidates = 1)
    )

    val service = new CandidateAllocationService(
      mockCandidateAllocationRepository,
      mockAppRepo,
      mockContactDetailsRepo,
      mockPersonalDetailsRepo,
      mockSchemeRepository,
      //override def schemeRepository: SchemeRepository = SchemeYamlRepository
      mockEventsService,
      mockAllocationServiceCommon,
      mockStcEventService,
      mockEmailClient,
      mockAuthProviderClient,
      mockMicroserviceAppConfig
//      override def eventsConfig: EventsConfig =
//        EventsConfig(scheduleFilePath = "", fsacGuideUrl = "", daysBeforeInvitationReminder = 1, maxNumberOfCandidates = 1)
    )

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

    def commonUnallocateCandidates(eligibleForReallocation: Boolean) = {
      val eventId = "E1"
      val sessionId = "S1"
      val appId = "app1"
      val candidateAllocations = CandidateAllocations("v1", eventId, sessionId, Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))
      val persistedAllocations: Seq[persisted.CandidateAllocation] = model.persisted.CandidateAllocation.fromCommand(candidateAllocations)

      when(mockCandidateAllocationRepository.isAllocationExists(any[String], any[String], any[String], any[Option[String]]))
        .thenReturnAsync(true)
      when(mockCandidateAllocationRepository.removeCandidateAllocation(any[persisted.CandidateAllocation])).thenReturnAsync()
      when(mockAppRepo.resetApplicationAllocationStatus(any[String], any[EventType])).thenReturnAsync()

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockAppRepo.find(List(appId))).thenReturnAsync(CandidateExamples.NewCandidates)
      when(mockPersonalDetailsRepo.find(any[String])).thenReturnAsync(PersonalDetailsExamples.JohnDoe)
      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(ContactDetailsExamples.ContactDetailsUK)

      when(mockEmailClient.sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturnAsync()

      service.unAllocateCandidates(persistedAllocations.toList, eligibleForReallocation).futureValue

      verify(mockEmailClient).sendCandidateUnAllocatedFromEvent(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
    }

    def allocatedCandidate(appId: String, userId: String, applicationStatus: ApplicationStatus) =
      model.Candidate(
        userId = userId,
        applicationId = Some(appId),
        testAccountId = None,
        email = None,
        firstName = Some("first"),
        lastName = Some("last"),
        preferredName = Some("first"),
        dateOfBirth = None,
        address = None,
        postCode = None,
        country = None,
        applicationRoute = Some(ApplicationRoute.Faststream),
        applicationStatus = Some(applicationStatus)
      )
  }
}
