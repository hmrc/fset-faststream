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

package controllers

import config.TestFixtureBase
import model.AllocationStatuses
import model.Exceptions.{ EventNotFoundException, OptimisticLockException }
import model.exchange._
import model.persisted.eventschedules.{ Event, EventType, Location, Venue, _ }
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.application.GeneralApplicationRepository
import repositories.events.{ LocationsWithVenuesRepository, UnknownVenueException }
import services.allocation.AssessorAllocationService
import services.events.EventsService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


class EventsControllerSpec extends UnitWithAppSpec {

  "Upload assessment events" must {
    "return CREATED with valid input" in new TestFixture {
      when(mockEventsService.saveAssessmentEvents()).thenReturnAsync()
      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe CREATED
    }

    "return UNPROCESSABLE_ENTITY when parsing goes wrong" in new TestFixture {
      when(mockEventsService.saveAssessmentEvents()).thenReturn(Future.failed(new Exception("Error")))
      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe UNPROCESSABLE_ENTITY
    }
  }

  "getEvents" must {
    "return OK with all events" in new TestFixture {
      when(mockEventsService.getEvents(any[EventType.EventType], any[Venue], any())).thenReturnAsync(List(event))
      status(controller.getEvents("FSAC", venue.name)(FakeRequest())) mustBe OK
    }

    "return OK when get events with description" in new TestFixture {
      when(mockEventsService.getEvents(eqTo(EventType.FSB), eqTo(venue), eqTo(Some("EAC")))).thenReturnAsync(List(event))
      status(controller.getEvents("FSB", venue.name, Some("EAC"))(FakeRequest())) mustBe OK
    }

    "return 400 for invalid event" in new TestFixture {
      status(controller.getEvents("blah", venue.name)(FakeRequest())) mustBe BAD_REQUEST
    }

    "return 400 for invalid venue type" in new TestFixture {
      when(mockLocationsWithVenuesRepo.venue("blah")).thenReturn(Future.failed(UnknownVenueException("")))
      status(controller.getEvents("FSAC", "blah")(FakeRequest())) mustBe BAD_REQUEST
    }
  }

  "getEvent" should {
    "return 200 for an event for an id" in new TestFixture {
      when(mockEventsService.getEvent(any[String])).thenReturnAsync(event)

      val result = controller.getEvent("id")(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(event)
    }

    "return a 404 if no event is found" in new TestFixture {
      when(mockEventsService.getEvent(any[String])).thenReturn(Future.failed(EventNotFoundException("")))
      val result = controller.getEvent("id")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }
  }

  "getEventsWithAllocationsSummary" should {
    "return OK" in new TestFixture {
      when(mockEventsService.getEventsWithAllocationsSummary(venue, EventType.FSAC, None)).thenReturnAsync(
        List(eventWithAllocationsSummaryWithDescription))
      status(controller.getEventsWithAllocationsSummary(venue.name, EventType.FSAC)(FakeRequest())) mustBe OK
    }
  }

  "getEventsWithAllocationsSummaryWithDescription" should {
    "return OK" in new TestFixture {
      when(mockEventsService.getEventsWithAllocationsSummary(venue, EventType.FSB, Some("EAC"))).thenReturnAsync(
        List(eventWithAllocationsSummaryWithDescription))
      status(controller.getEventsWithAllocationsSummaryWithDescription(venue.name, EventType.FSB, "EAC")(FakeRequest())) mustBe OK
    }
  }

  "Allocate assessor" must {
    "return a 409 if an op lock exception occurs" in new TestFixture {
      when(mockAssessorAllocationService.allocate(any[model.command.AssessorAllocations])(any[HeaderCarrier]))
        .thenReturn(Future.failed(OptimisticLockException("error")))

      val request = fakeRequest(AssessorAllocations(
        version = Some("version1"),
        AssessorAllocation("id", AllocationStatuses.CONFIRMED, AssessorSkill(SkillType.ASSESSOR,"Assessor")) :: Nil
      ))
      val result = controller.allocateAssessor("eventId")(request)
      status(result) mustBe CONFLICT
    }
  }


  trait TestFixture extends TestFixtureBase {
    val mockEventsService = mock[EventsService]
    val mockAssessorAllocationService = mock[AssessorAllocationService]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]

    val controller = new EventsController {
      val eventsService = mockEventsService
      val assessorAllocationService = mockAssessorAllocationService
      val locationsAndVenuesRepository: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
      val applicationRepository = mockAppRepo
    }

    val venue = Venue("London FSAC", "Bush House")
    val location = Location("London")

    when(mockLocationsWithVenuesRepo.location(any[String])).thenReturnAsync(location)
    when(mockLocationsWithVenuesRepo.venue(eqTo(venue.name))).thenReturnAsync(venue)


    val event = new Event("id", EventType.FSAC, "description", location, venue,
            LocalDate.now, 32, 10, 5, LocalTime.now, LocalTime.now, DateTime.now, Map.empty, List.empty)
    val eventWithAllocationsSummaryWithDescription = new EventWithAllocationsSummary(LocalDate.now, event, Nil, Nil)

  }

}
