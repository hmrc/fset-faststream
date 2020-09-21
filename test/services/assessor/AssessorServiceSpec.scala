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

package services.assessor

import connectors.ExchangeObjects.Candidate
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.AllocationStatuses.AllocationStatus
import model.Exceptions._
import model.exchange.{ AssessorAvailabilities, UpdateAllocationStatusRequest }
import model.persisted.EventExamples._
import model.persisted.assessor.AssessorExamples._
import model.persisted.assessor.{ Assessor, AssessorAvailability, AssessorStatus }
import model.persisted.eventschedules._
import model.persisted.{ AssessorAllocation, EventExamples, ReferenceData }
import model.{ AllocationStatuses, Exceptions, UniqueIdentifier }
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.events.LocationsWithVenuesRepository
import repositories.{ AssessorAllocationRepository, AssessorRepository }
import services.BaseServiceSpec
import services.events.EventsService
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

class AssessorServiceSpec extends BaseServiceSpec {

  "save assessor" must {
    "save NEW assessor " +
      "when assessor is new" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(None)
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update skills and do not update existing availability " +
      "when assessor previously EXISTED but did not have future allocations to events" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(Some(AssessorExisting))
      when(mockAllocationRepo.find(eqTo(AssessorUserId), any[Option[AllocationStatus]]())).thenReturnAsync(Nil)

      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      val expectedAssessor = AssessorNew.copy(version = NewVersion, availability = AssessorExisting.availability,
        status = AssessorStatus.AVAILABILITIES_SUBMITTED)
      verify(mockAssessorRepository).save(eqTo(expectedAssessor))
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

    "return assessor to events mapping since a specified date" in new AssessorsEventsSummaryFixture {
      val result = service.assessorToEventsMappingSince(DateTime.now).futureValue
      val resultKeys = result.keys.toList
      resultKeys.size mustBe assessorToEventsMapping.keys.toList.size
      resultKeys.foreach { key =>
        assessorToEventsMapping.keys.toList.contains(key) mustBe true
      }
      result.foreach { case (assessor, assessorEvents) =>
        assessorEvents mustBe assessorToEventsMapping(assessor)
      }
    }

    "notify assessors of new events" in new AssessorsEventsSummaryFixture {
      val now = DateTime.now
      val result = service.notifyAssessorsOfNewEvents(now)(new HeaderCarrier).futureValue
      val emailBodyCaptor = ArgumentCaptor.forClass(classOf[String])
      verify(mockemailClient, times(2)).notifyAssessorsOfNewEvents(
        to = any[String], name = any[String], htmlBody = any[String], txtBody = emailBodyCaptor.capture)(any[HeaderCarrier])

      val emails = emailBodyCaptor.getAllValues
      val emailsForEvent1 = emails.get(0).toString
      val emailsForEvent2 = emails.get(1).toString

      val fsacVirtual = s"${now.toString("EEEE, dd MMMM YYYY")} (FSAC - Virtual)"
      val event1ExpectedEmails = s"$fsacVirtual\n$fsacVirtual"
      emailsForEvent1 mustBe event1ExpectedEmails

      val fsbLondon = s"${now.toString("EEEE, dd MMMM YYYY")} (FSB - London)"
      val fsbNewcastle = s"${now.toString("EEEE, dd MMMM YYYY")} (FSB - Newcastle)"
      val event2ExpectedEmails = s"$fsacVirtual\n$fsbLondon\n$fsbNewcastle"
      emailsForEvent2 mustBe event2ExpectedEmails
    }
  }

  "saveAssessor" must {
    "update assessor " +
      "when we add skills even if there are future allocations" in new TestFixtureWithFutureAllocations {
      when(mockAssessorRepository.save(any())).thenReturnAsync()
      service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorWith2Skill)).futureValue
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update assessor " +
      "when we keep the skills even if there are future allocations" in new TestFixtureWithFutureAllocations {
      when(mockAssessorRepository.save(any())).thenReturnAsync()
      val AssessorToSave = AssessorWithSkill.copy(civilServant = !AssessorWithSkill.civilServant)
      service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorToSave)).futureValue
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update assessor " +
      "when we remove skill where the assessor has not future allocations" in new TestFixtureWithFutureAllocations {
      val assessor = AssessorWith2Skill
      val userId = assessor.userId
      override val futureAllocations = Seq(AssessorAllocation(userId, eventId, AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, ""))
      when(mockAllocationRepo.find(userId)).thenReturnAsync(futureAllocations)
      when(mockAssessorRepository.find(eqTo(userId))).thenReturnAsync(Some(assessor))
      val AssessorToSave = assessor.copy(skills = List("ASSESSOR"))

      service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorToSave)).futureValue
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update assessor " +
      "when we remove skill where the assessor has allocations in the past" in new TestFixtureWithFutureAllocations {
      override val futureEvent = EventExamples.e1.copy(id = eventId, eventType = EventType.FSAC, date = LocalDate.now().minusDays(1))
      when(mockEventService.getEvent(eventId)).thenReturnAsync(futureEvent)
      val AssessorToSave = AssessorWithSkill.copy(skills = List("SIFTER"))

      service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorToSave)).futureValue
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "throw CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException " +
      "when we remove skill where the assessor has future allocations" in new TestFixtureWithFutureAllocations {
      val AssessorToSave = AssessorWithSkill.copy(skills = List("SIFTER"))

      intercept[CannotUpdateAssessorWhenSkillsAreRemovedAndFutureAllocationExistsException] {
        Await.result(service.saveAssessor(AssessorUserId, model.exchange.Assessor(AssessorToSave)), 10 seconds)
      }

      verify(mockAssessorRepository, times(0)).save(any[Assessor])
    }
  }

  "removeAssessor" should {
    "throw AssessorNotFoundException " +
      "when assessor does not exist" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturnAsync(None)
      intercept[AssessorNotFoundException] {
        Await.result(service.remove(UniqueIdentifier(AssessorUserId)), 10 seconds)
      }
      verify(mockAssessorRepository, times(0)).remove(eqTo(UniqueIdentifier(AssessorUserId)))
    }

    "throw CannotRemoveAssessorWhenFutureAllocationExistsException " +
      "when assessor exists and there are future allocations in the same skills" in new TestFixtureWithFutureAllocations {
      intercept[CannotRemoveAssessorWhenFutureAllocationExistsException] {
        Await.result(service.remove(UniqueIdentifier(AssessorUserId)), 10 seconds)
      }
      verify(mockAssessorRepository, times(0)).remove(eqTo(UniqueIdentifier(AssessorUserId)))
    }

    "remove assessor " +
      "when assessor exists and there are allocations with those skills but none in the future" in new TestFixtureWithFutureAllocations {
      override val futureEvent = EventExamples.e1.copy(id = eventId, eventType = EventType.FSAC, date = LocalDate.now().minusDays(1))
      when(mockEventService.getEvent(eventId)).thenReturnAsync(futureEvent)

      when(mockAssessorRepository.remove(eqTo(UniqueIdentifier(AssessorUserId)))).thenReturnAsync()

      service.remove(UniqueIdentifier(AssessorUserId)).futureValue
      verify(mockAssessorRepository).remove(eqTo(UniqueIdentifier(AssessorUserId)))
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

    val nonSpiedService = new AssessorService {
      val assessorRepository: AssessorRepository = mockAssessorRepository
      val assessorAllocationRepo: AssessorAllocationRepository = mockAllocationRepo
      val eventsService: EventsService = mockEventService
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
      val authProviderClient: AuthProviderClient = mockAuthProviderClient
      val emailClient: EmailClient = mockemailClient
    }

    import org.mockito.Mockito

    val NewVersion = nonSpiedService.newVersion
    val service = Mockito.spy(nonSpiedService)

    when(service.newVersion).thenReturn(NewVersion)
  }

  trait AssessorsEventsSummaryFixture extends TestFixture {

    val e1 = Event(id = "eventId1", eventType = EventType.FSAC, description = "GCFS FSB", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.ASSESSOR.toString -> 1,
        SkillType.QUALITY_ASSURANCE_COORDINATOR.toString -> 1), sessions = List())

    val e2 = Event(id = "eventId2", eventType = EventType.FSB, description = "ORAC", location = LocationLondon,
      venue = VenueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      createdAt = DateTime.now, skillRequirements = Map(SkillType.CHAIR.toString -> 1), sessions = List())

    val e3 = Event(id = "eventId3", eventType = EventType.FSB, description = "GCFS FSB",
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
    val assessors: Seq[Assessor] = Seq(a1, a2)

    val findByUserIdsResponse = Seq(
      Candidate("Joe", "Bloggs", None, "joe.bloggs@test.com", None, "userId1", List("assessor")),
      Candidate("John", "Bloggs", None, "john.bloggs@test.com", None, "userId2", List("assessor")),
      Candidate("Bill", "Bloggs", None, "bill.bloggs@test.com", None, "userId3", List("assessor"))
    )

    val assessorToEventsMapping: Map[Assessor, Seq[Event]] = Map[Assessor, Seq[Event]](
      a1 -> Seq(e1, e2, e3),
      a2 -> Seq(e1, e4)
    )

    when(mockEventService.getEventsCreatedAfter(any[DateTime])).thenReturn(Future(events))
    when(mockAssessorRepository.findUnavailableAssessors(
      eqTo(Seq(SkillType.ASSESSOR, SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturnAsync(Seq(a1, a2))
    when(mockAssessorRepository.findUnavailableAssessors(
      eqTo(Seq(SkillType.CHAIR)), any[Location], any[LocalDate])).thenReturnAsync(Seq(a1))
    when(mockAssessorRepository.findUnavailableAssessors(
      eqTo(Seq(SkillType.ASSESSOR)), any[Location], any[LocalDate])).thenReturnAsync(Seq(a1))
    when(mockAssessorRepository.findUnavailableAssessors(
      eqTo(Seq(SkillType.QUALITY_ASSURANCE_COORDINATOR)), any[Location], any[LocalDate])).thenReturnAsync(Seq(a2))
    when(mockAuthProviderClient.findByUserIds(any[Seq[String]])(any[HeaderCarrier])).thenReturnAsync(findByUserIdsResponse)
    when(mockemailClient.notifyAssessorsOfNewEvents(any[String], any[String], any[String], any[String])(any[HeaderCarrier])).thenReturnAsync()
  }

  trait TestFixtureWithFutureAllocations extends TestFixture {
    when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(Some(AssessorWithSkill))

    val eventId = "eventId"
    val futureEvent = EventExamples.e1.copy(id = eventId, eventType = EventType.FSAC, date = LocalDate.now().plusDays(1))
    when(mockEventService.getEvent(eqTo(eventId))).thenReturnAsync(futureEvent)

    val futureAllocations = Seq(AssessorAllocation(AssessorUserId, eventId, AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, ""))
    when(mockAllocationRepo.find(AssessorUserId)).thenReturnAsync(futureAllocations)
  }
}
