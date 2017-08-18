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

package services.assessor

import connectors.ExchangeObjects.Candidate
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.Exceptions.AssessorNotFoundException
import model.exchange.{ AssessorAvailabilities, UpdateAllocationStatusRequest }
import model.persisted.EventExamples._
import model.persisted.assessor.{ Assessor, AssessorAvailability, AssessorStatus }
import model.persisted.assessor.AssessorExamples._
import model.persisted.eventschedules._
import model.persisted.{ EventExamples, ReferenceData }
import model.{ AllocationStatuses, Exceptions }
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.events.LocationsWithVenuesRepository
import repositories.{ AssessorAllocationRepository, AssessorRepository }
import services.BaseServiceSpec
import services.assessoravailability.AssessorService
import services.events.EventsService
import testkit.MockitoImplicits._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class AssessorServiceSpec extends BaseServiceSpec {

  "save assessor" must {

    "save NEW assessor when assessor is new" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(None)
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update skills and do not update availability when assessor previously EXISTED" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(Some(AssessorExisting))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(any[Assessor])
    }
  }

  "save availability" must {

    "throw assessor not found exception when assessor cannot be found" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(None)

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)
      val availabilities = AssessorAvailabilities(AssessorUserId, None, exchangeAvailability)


      intercept[AssessorNotFoundException] {
        Await.result(service.saveAvailability(availabilities), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "save availability to EXISTING assessor" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(Some(AssessorExisting))
      when(mockLocationsWithVenuesRepo.location(any[String])).thenReturnAsync(EventExamples.LocationLondon)

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)

      val availabilties = AssessorAvailabilities(AssessorUserId, None, exchangeAvailability)
      val result = service.saveAvailability(availabilties).futureValue

      result mustBe unit

      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(any[Assessor])
    }
  }


  "find assessor" must {
    "return assessor details" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorExisting))

      val response = service.findAssessor(AssessorUserId).futureValue

      response mustBe model.exchange.Assessor(AssessorExisting)
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there is no assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(None)

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAssessor(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  "find assessor availability" must {

    "return assessor availability" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorWithAvailability))
      val response = service.findAvailability(AssessorUserId).futureValue
      val expected = AssessorWithAvailability.availability.map { a => model.exchange.AssessorAvailability.apply(a) }
      response mustBe AssessorAvailabilities(AssessorUserId, response.version, expected)
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there are is assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(None)

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAvailability(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  "updating an assessors allocation status" must {
    "return a successful update response" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorWithAvailability))
      when(mockAllocationRepo.updateAllocationStatus(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED)).thenReturnAsync()
      val updates = UpdateAllocationStatusRequest(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe Nil
      result.successes mustBe updates
    }

    "return a failed response" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorWithAvailability))
      when(mockAllocationRepo.updateAllocationStatus(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.failed(new Exception("something went wrong")))

      val updates = UpdateAllocationStatusRequest(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe updates
      result.successes mustBe Nil
    }

    "return a partial update response" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorWithAvailability))
      when(mockAllocationRepo.updateAllocationStatus(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED)).thenReturnAsync()
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(Some(AssessorWithAvailability))
      when(mockAllocationRepo.updateAllocationStatus(AssessorUserId, "eventId2", AllocationStatuses.CONFIRMED))
        .thenReturn(Future.failed(new Exception("something went wrong")))

      val updates = UpdateAllocationStatusRequest(AssessorUserId, "eventId", AllocationStatuses.CONFIRMED) ::
        UpdateAllocationStatusRequest(AssessorUserId, "eventId2", AllocationStatuses.CONFIRMED) :: Nil
      val result = service.updateAssessorAllocationStatuses(updates).futureValue
      result.failures mustBe updates.last :: Nil
      result.successes mustBe updates.head :: Nil
    }

    "return assessor to events mapping since a specified date" in new TestFixture with AssessorsEventsSummaryData {
      when(mockEventService.getEventsCreatedAfter(any[DateTime])).thenReturn(Future(events))
      when(mockAssessorRepository.findUnavailableAssessors(
        eqTo(Seq(SkillType.ASSESSOR, SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1, a2)))
      when(mockAssessorRepository.findUnavailableAssessors(
        eqTo(Seq(SkillType.CHAIR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1)))
      when(mockAssessorRepository.findUnavailableAssessors(
        eqTo(Seq(SkillType.ASSESSOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1)))
      when(mockAssessorRepository.findUnavailableAssessors(
        eqTo(Seq(SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a2)))

      val result = service.assessorToEventsMappingSince(DateTime.now).futureValue
      val resultKeys = result.keys.toList
      resultKeys.size mustBe assessorToEventsMapping.keys.toList.size
      resultKeys.foreach(assessorToEventsMapping.keys.toList.contains)
      result.foreach{ case (assessor, assessorEvents) =>
        assessorEvents mustBe assessorToEventsMapping(assessor)
      }
    }

    "notify assessors of new events" ignore new TestFixture with AssessorsEventsSummaryData {
      // TODO: Verify that the email client gets called with the right arguments
      when(mockEventService.getEventsCreatedAfter(any[DateTime])).thenReturn(Future(events))
      when(service.findUnavailableAssessors(
        eqTo(Seq(SkillType.ASSESSOR, SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1, a2)))
      when(service.findUnavailableAssessors(
        eqTo(Seq(SkillType.CHAIR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1)))
      when(service.findUnavailableAssessors(
        eqTo(Seq(SkillType.ASSESSOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a1)))
      when(service.findUnavailableAssessors(
        eqTo(Seq(SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturn(Future(Seq(a2)))

//      when(service.assessorToEventsMappingSince(any[DateTime])).thenReturn(Future(assessorToEventsMapping))
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturn(Future(findByUserIdsResponse))

      val result = service.notifyAssessorsOfNewEvents()(any[HeaderCarrier]).futureValue
      verify(mockemailClient, times(2)).notifyAssessorsOfNewEvents(any[String], any[String], any[String], any[String])(any[HeaderCarrier])
    }
  }

  trait TestFixture {
    val mockAssessorRepository = mock[AssessorRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val mockAllocationRepo = mock[AssessorAllocationRepository]
    val mockEventService = mock[EventsService]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val mockemailClient = mock[CSREmailClient]
    val virtualVenue = Venue("virtual", "virtual venue")
    val venues = ReferenceData(List(Venue("london fsac", "bush house"), virtualVenue), virtualVenue, virtualVenue)

    when(mockLocationsWithVenuesRepo.venues).thenReturnAsync(venues)
    when(mockAssessorRepository.save(any[Assessor])).thenReturnAsync()

    val service = new AssessorService {
      val assessorRepository: AssessorRepository = mockAssessorRepository
      val allocationRepo: AssessorAllocationRepository = mockAllocationRepo
      val eventsService: EventsService = mockEventService
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
      val authProviderClient: AuthProviderClient = mockAuthProviderClient
      val emailClient: EmailClient = mockemailClient
    }
  }

  trait AssessorsEventsSummaryData {

    val e1 = Event(id = "eventId1", eventType = EventType.FSAC, description = "GCFS FSB", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.ASSESSOR.toString -> 1,
        SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 1), sessions = List())

    val e2 = Event(id = "eventId2", eventType = EventType.TELEPHONE_INTERVIEW, description = "ORAC", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.CHAIR.toString -> 1), sessions = List())

    val e3 = Event(id = "eventId3", eventType = EventType.SKYPE_INTERVIEW, description = "GCFS FSB",
      location = LocationNewcastle, venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.ASSESSOR.toString -> 1), sessions = List())

    val e4 = Event(id = "eventId4", eventType = EventType.FSAC, description = "DFS FSB", location = LocationNewcastle,
      venue = VenueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 1), sessions = List())

    val events = Seq(e1, e2, e3, e4)
    val a1Skills = List(SkillType.ASSESSOR, SkillType.CHAIR)
    val a2Skills = List(SkillType.QUALITY_ASSURANCE_COORDINATOR)

    val availabilities = Set(AssessorAvailability(Location("london"), new LocalDate(2017, 8, 11)))
    val a1 = Assessor("userId1", None, a1Skills.map(_.toString), Nil, civilServant = true, Set.empty, AssessorStatus.CREATED)
    val a2 = Assessor("userId2", None, a2Skills.map(_.toString), Nil, civilServant = true, Set.empty, AssessorStatus.CREATED)
    val a3 = Assessor("userId3", None, a1Skills.map(_.toString), Nil, civilServant = true, availabilities, AssessorStatus.CREATED)
    val assessors: Seq[Assessor] = Seq (a1, a2)

    val findByUserIdsResponse = Seq(
      Candidate("Joe", "Bloggs", None, "joe.bloggs@test.com", "userId1"),
      Candidate("John", "Bloggs", None, "john.bloggs@test.com", "userId2"),
      Candidate("Bill", "Bloggs", None, "bill.bloggs@test.com", "userId3")
    )

    val assessorToEventsMapping: Map[Assessor, Seq[Event]] = Map[Assessor, Seq[Event]](
      a1 -> Seq(e1, e2, e3),
      a2 -> Seq(e1, e4)
    )
  }

}
