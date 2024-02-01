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
import model.Exceptions.CandidateAlreadyAssignedToOtherEventException
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
import repositories.fsb.FsbRepository
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

class CandidateAllocationServiceSpec extends BaseServiceSpec with ExtendedTimeout {
  "Allocate candidate" must {
    "save allocation if none already exists" in new TestFixture {
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId = "appId1"
      val version = "v1"
      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Nil)
      //here
//      when(mockCandidateAllocationRepository.activeAllocationsForSession(eventId, sessionId)).thenReturnAsync(Nil)
      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(
        exchange.CandidateAllocations(version = None, allocations = Nil)
      )

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(Nil)

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

    "fail to save allocation to event with no allocations if candidate has already been allocated to a different event" in new TestFixture {
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val version = "v1"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Seq(EventExamples.e3))

      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(
        exchange.CandidateAllocations(version = None, allocations = Nil)
      )

      val existingAllocation = model.persisted.CandidateAllocation(
        "appId", "eventId", "sessionId", AllocationStatuses.CONFIRMED, "v1",
        removeReason = None, LocalDate.now(), reminderSent = false)

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]]))
        .thenReturnAsync(Seq(existingAllocation))

      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation("appId1", AllocationStatuses.UNCONFIRMED)))

      val result = service.allocateCandidates(candidateAllocations, append = false).failed.futureValue
      result mustBe a[CandidateAlreadyAssignedToOtherEventException]
    }

    "fail to save allocation to event with allocations if candidate has already been allocated to a different event" in new TestFixture {
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val version = "v1"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Seq(EventExamples.e3))

      when(mockAllocationServiceCommon.getCandidateAllocations(eventId, sessionId)).thenReturnAsync(
        exchange.CandidateAllocations(
          version = Some("v1"),
          allocations = Seq(model.exchange.CandidateAllocation("appId", AllocationStatuses.CONFIRMED, removeReason = None))
        )
      )

      val existingAllocation = model.persisted.CandidateAllocation(
        "appId", "eventId", "sessionId", AllocationStatuses.CONFIRMED, "v1",
        removeReason = None, LocalDate.now(), reminderSent = false)

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]]))
        .thenReturnAsync(Seq(existingAllocation))

      val candidateAllocations = CandidateAllocations(version, eventId, sessionId,
        Seq(CandidateAllocation("appId1", AllocationStatuses.UNCONFIRMED)))

      val result = service.allocateCandidates(candidateAllocations, append = false).failed.futureValue
      result mustBe a[CandidateAlreadyAssignedToOtherEventException]
    }

    "exclude candidates who have been offered a job when processing new allocations for the same event" in new TestFixture {
      val version = "v1"
      val eventId = "E1"
      val sessionId = "71bbe093-cfc6-4cec-ad1a-e15ae829d7b0"
      val appId1 = "appId1"
      val appId2 = "appId2"

      when(mockEventsService.getEvent(eventId)).thenReturnAsync(EventExamples.e1)
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Nil)

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

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(Nil)

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
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Nil)

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

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(Nil)
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
      when(mockEventsService.getEvents(any[EventType])).thenReturnAsync(Nil)

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

      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(Nil)
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

  "Checking a candidate is already assigned to an event" must {
    "prevent a FSAC candidate being assigned to 2 events at the same time" in new TestFixture {
      // Event 1 - candidate already assigned
      val fsacEvent1 = Event(
        id = "eventId1", eventType = EventType.FSAC, description = "", location = Location("London"),
        venue = Venue("LONDON_FSAC", "London (100 Parliament Street)"),
        date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55, attendeeSafetyMargin = 10,
        startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 2 - we are now attempting to assign the candidate to this fsac event
      val fsacEvent2 = Event(
        id = "eventId2", eventType = EventType.FSAC, description = "", location = Location("London"),
        venue = Venue("LONDON_FSAC", "London (100 Parliament Street)"),
        date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55, attendeeSafetyMargin = 10,
        startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // We are trying to assign the candidate to fsacEvent2
      when(mockEventsService.getEvent(fsacEvent2.id)).thenReturnAsync(fsacEvent2)

      // Candidate has already been assigned to fsacEvent1
      when(mockEventsService.getEvents(EventType.FSAC)).thenReturnAsync(Seq(fsacEvent1))

      // This returns the events the candidate has already been assigned to
      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId1",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          )
        )
      )

      val newAllocations = command.CandidateAllocations("version", "eventId2", "sessionId1",
        allocations = Seq(CandidateAllocation("applicationId", AllocationStatuses.CONFIRMED))
      )

      // A value of true indicates that the candidate cannot be assigned to the new event
      service.isAlreadyAssignedToAnEvent("eventId2", newAllocations).futureValue mustBe true
    }

    "allow a FSB candidate who has failed their first FSB event to be assigned to a second event" in new TestFixture {
      // Event 1 - candidate already assigned and failed the fsb for ProjectDelivery
      val projectDeliveryFsbEvent = Event(
        id = "eventId1", eventType = EventType.FSB, description = "PDFS - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 2 - we are now attempting to assign the candidate to this fsb event for Property
      val propertyFsbEvent = Event(
        id = "eventId2", eventType = EventType.FSB, description = "PRO - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // We are trying to assign the candidate to "PRO - Skype interview" FSB for Property scheme
      when(mockEventsService.getEvent(propertyFsbEvent.id)).thenReturnAsync(propertyFsbEvent)

      // Candidate has already been assigned to the PDFS - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      // called from within isFsbCandidateWithFailedEvents
      when(mockEventsService.getEvent(eqTo(projectDeliveryFsbEvent.id))).thenReturnAsync(projectDeliveryFsbEvent)

      // Candidate has already been assigned to the PDFS - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      when(mockEventsService.getEvents(EventType.FSB)).thenReturnAsync(Seq(projectDeliveryFsbEvent))

      // This returns the events the candidate has already been assigned to
      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId1",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          )
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(projectDeliveryFsbEvent.description)).thenReturn(
        Scheme(
          SchemeId("ProjectDelivery"), "PDFS", "Project Delivery",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PDFS - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockFsbRepo.findByApplicationId("applicationId")).thenReturnAsync(
        Some(
          FsbTestGroup(FsbEvaluation(List(SchemeEvaluationResult(SchemeId("ProjectDelivery"), model.EvaluationResults.Red.toString))))
        )
      )

      val newAllocations = command.CandidateAllocations("version", "eventId2", "sessionId1",
        allocations = Seq(CandidateAllocation("applicationId", AllocationStatuses.CONFIRMED))
      )

      // A value of false indicates that the candidate can be assigned to the new event
      service.isAlreadyAssignedToAnEvent("eventId2", newAllocations).futureValue mustBe false
    }

    "allow a FSB candidate who has failed their first and second FSB events to be assigned to a third event" in new TestFixture {
      // Event 1 - candidate already assigned and failed the fsb for ProjectDelivery
      val projectDeliveryFsbEvent = Event(
        id = "eventId1", eventType = EventType.FSB, description = "PDFS - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 2 - candidate already assigned and failed the fsb for property as well
      val propertyFsbEvent = Event(
        id = "eventId2", eventType = EventType.FSB, description = "PRO - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 3 - we are now attempting to assign the candidate to the fsb for ScienceAndEngineering
      val scienceAndEngineeringFsbEvent = Event(
        id = "eventId3", eventType = EventType.FSB, description = "SEFS FSB", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // We are trying to assign the candidate to "SEFS FSB" FSB for ScienceAndEngineering scheme
      when(mockEventsService.getEvent(scienceAndEngineeringFsbEvent.id)).thenReturnAsync(scienceAndEngineeringFsbEvent)

      // Candidate has already been assigned to the PDFS - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      // called from within isFsbCandidateWithFailedEvents
      when(mockEventsService.getEvent(eqTo(projectDeliveryFsbEvent.id))).thenReturnAsync(projectDeliveryFsbEvent)

      // Candidate has also already been assigned to the PRO - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      // called from within isFsbCandidateWithFailedEvents
      when(mockEventsService.getEvent(eqTo(propertyFsbEvent.id))).thenReturnAsync(propertyFsbEvent)

      // Candidate has already been assigned to the fsb events for ProjectDelivery and Property schemes and the candidate failed both
      when(mockEventsService.getEvents(EventType.FSB)).thenReturnAsync(Seq(projectDeliveryFsbEvent, propertyFsbEvent))

      // This returns the events the candidate has already been assigned to
      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId1",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          ),
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId2",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          )
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(projectDeliveryFsbEvent.description)).thenReturn(
        Scheme(
          SchemeId("ProjectDelivery"), "PDFS", "Project Delivery",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PDFS - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(propertyFsbEvent.description)).thenReturn(
        Scheme(
          SchemeId("Property"), "PRO", "Property",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PRO - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockFsbRepo.findByApplicationId("applicationId")).thenReturnAsync(
        Some(
          FsbTestGroup(FsbEvaluation(List(
            SchemeEvaluationResult(SchemeId("ProjectDelivery"), model.EvaluationResults.Red.toString),
            SchemeEvaluationResult(SchemeId("Property"), model.EvaluationResults.Red.toString)
          )))
        )
      )

      val newAllocations = command.CandidateAllocations("version", "eventId3", "sessionId1",
        allocations = Seq(CandidateAllocation("applicationId", AllocationStatuses.CONFIRMED))
      )

      // A value of false indicates that the candidate can be assigned to the new event
      service.isAlreadyAssignedToAnEvent("eventId3", newAllocations).futureValue mustBe false
    }

    "prevent a FSB candidate being assigned to 2 events at the same time" in new TestFixture {
      // Event 1 - the candidate has already been assigned to this fsb for ProjectDelivery
      val projectDeliveryFsbEvent1 = Event(
        id = "eventId1", eventType = EventType.FSB, description = "PDFS - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 2 - we are now attempting to assign the candidate to a different fsb event also for ProjectDelivery
      val projectDeliveryFsbEvent2 = Event(
        id = "eventId2", eventType = EventType.FSB, description = "PDFS - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // We are trying to assign the candidate to "PRO - Skype interview" FSB for Property scheme
      when(mockEventsService.getEvent(projectDeliveryFsbEvent2.id)).thenReturnAsync(projectDeliveryFsbEvent2)

      // Candidate has already been assigned to "PRO - Skype interview" FSB for Property scheme by another admin
      // called from within isFsbCandidateWithFailedEvents
      when(mockEventsService.getEvent(eqTo(projectDeliveryFsbEvent1.id))).thenReturnAsync(projectDeliveryFsbEvent1)

      // Candidate has already been assigned to "PRO - Skype interview" FSB for Property scheme by another admin
      when(mockEventsService.getEvents(EventType.FSB)).thenReturnAsync(Seq(projectDeliveryFsbEvent1))

      // This returns the events the candidate has already been assigned to
      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId1",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          )
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(projectDeliveryFsbEvent1.description)).thenReturn(
        Scheme(
          SchemeId("ProjectDelivery"), "PDFS", "Project Delivery",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PDFS - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockFsbRepo.findByApplicationId("applicationId")).thenReturnAsync(None)

      val newAllocations = command.CandidateAllocations("version", "eventId2", "sessionId1",
        allocations = Seq(CandidateAllocation("applicationId", AllocationStatuses.CONFIRMED))
      )

      // A value of true indicates that the candidate cannot be assigned to the new event
      service.isAlreadyAssignedToAnEvent("eventId2", newAllocations).futureValue mustBe true
    }

    "prevent a FSB candidate being assigned to 2 events at the same time after failing other fsbs" in new TestFixture {
      // Event 1 - candidate already assigned and failed the fsb for ProjectDelivery
      val projectDeliveryFsbEvent = Event(
        id = "eventId1", eventType = EventType.FSB, description = "PDFS - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 2 - candidate has already been assigned to this fsb event for Property by another admin
      val propertyFsbEvent1 = Event(
        id = "eventId2", eventType = EventType.FSB, description = "PRO - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // Event 3 - at the same time we are now attempting to assign the candidate to this fsb event for Property
      val propertyFsbEvent2 = Event(
        id = "eventId3", eventType = EventType.FSB, description = "PRO - Skype interview", location = Location("Virtual"),
        venue = Venue("VIRTUAL", "Virtual"), date = LocalDate.now().plusDays(2), capacity = 60, minViableAttendees = 55,
        attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), createdAt = now,
        skillRequirements = Map(), sessions = List()
      )

      // We are trying to assign the candidate to "PRO - Skype interview" FSB for Property scheme
      when(mockEventsService.getEvent(propertyFsbEvent2.id)).thenReturnAsync(propertyFsbEvent2)

      // Candidate has already been assigned to the PDFS - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      // called from within isFsbCandidateWithFailedEvents
      when(mockEventsService.getEvent(eqTo(projectDeliveryFsbEvent.id))).thenReturnAsync(projectDeliveryFsbEvent)
      when(mockEventsService.getEvent(eqTo(propertyFsbEvent1.id))).thenReturnAsync(propertyFsbEvent1)

      // Candidate has already been assigned to the PDFS - Skype interview FSB for ProjectDelivery scheme and the candidate failed
      when(mockEventsService.getEvents(EventType.FSB)).thenReturnAsync(Seq(projectDeliveryFsbEvent, propertyFsbEvent1))

      // This returns the events the candidate has already been assigned to
      when(mockCandidateAllocationRepository.findAllConfirmedOrUnconfirmedAllocations(any[Seq[String]], any[Seq[String]])).thenReturnAsync(
        Seq(
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId1",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          ),
          model.persisted.CandidateAllocation(
            "applicationId",
            "eventId2",
            "sessionId1",
            AllocationStatuses.CONFIRMED,
            "version",
            removeReason = None,
            createdAt = LocalDate.now,
            reminderSent = false
          )
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(projectDeliveryFsbEvent.description)).thenReturn(
        Scheme(
          SchemeId("ProjectDelivery"), "PDFS", "Project Delivery",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PDFS - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockSchemeRepository.getSchemeForFsb(propertyFsbEvent1.description)).thenReturn(
        Scheme(
          SchemeId("Property"), "PRO", "Property",
          civilServantEligible = true,
          degree = None,
          siftRequirement = None,
          siftEvaluationRequired = false,
          fsbType = Some(FsbType("PRO - Skype interview")),
          schemeGuide = None,
          schemeQuestion = None
        )
      )

      when(mockFsbRepo.findByApplicationId("applicationId")).thenReturnAsync(
        Some(
          FsbTestGroup(FsbEvaluation(List(SchemeEvaluationResult(SchemeId("ProjectDelivery"), model.EvaluationResults.Red.toString))))
        )
      )
      when(mockFsbRepo.findByApplicationId("applicationId")).thenReturnAsync(
        Some(
          FsbTestGroup(FsbEvaluation(List(SchemeEvaluationResult(SchemeId("ProjectDelivery"), model.EvaluationResults.Red.toString))))
        )
      )

      val newAllocations = command.CandidateAllocations("version", "eventId3", "sessionId1",
        allocations = Seq(CandidateAllocation("applicationId", AllocationStatuses.CONFIRMED))
      )

      // A value of true indicates that the candidate cannot be assigned to the new event
      service.isAlreadyAssignedToAnEvent("eventId3", newAllocations).futureValue mustBe true
    }
  }

  trait TestFixture extends StcEventServiceFixture {
    val mockCandidateAllocationRepository = mock[CandidateAllocationMongoRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockPersonalDetailsRepo = mock[PersonalDetailsRepository]
    val mockSchemeRepository = mock[SchemeRepository]
    val mockFsbRepo = mock[FsbRepository]
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
      mockFsbRepo,
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
