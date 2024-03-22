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

package controllers

import config.TestFixtureBase
import model.Exceptions.{CandidateAlreadyAssignedToOtherEventException, OptimisticLockException}
import model.{AllocationStatuses, FSACIndicator}
import model.exchange.{CandidateAllocation, CandidateAllocations, CandidateEligibleForEvent, CandidatesEligibleForEventResponse}
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{EventType, Location, Venue}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.RequestHeader
import play.api.test.Helpers.{contentAsJson, status, _}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import services.allocation.CandidateAllocationService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import scala.concurrent.Future

class CandidateAllocationControllerSpec  extends UnitWithAppSpec {

  private val location = "London"
  private val eventType = EventType.FSAC
  private val description = "ORAC"

  "Find candidates eligible for event allocation" must {
    "handle no candidates" in new TestFixture {
      when(mockCandidateAllocationService.findCandidatesEligibleForEventAllocation(any[String], any[EventType], any[String]))
        .thenReturnAsync(CandidatesEligibleForEventResponse(List.empty, 0))

      val result = controller.findCandidatesEligibleForEventAllocation(location, eventType, description)(
        findCandidatesEligibleForEventAllocationRequest(location, eventType, description)).run()
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "candidates").as[List[CandidateEligibleForEvent]] mustBe List.empty
      (jsonResponse \ "totalCandidates").as[Int] mustBe 0

      status(result) mustBe OK
    }

    "handle candidates" in new TestFixture {
      val fsacIndicator = FSACIndicator("SouthWest London", "London")
      val candidate = CandidateEligibleForEvent(applicationId = "appId", firstName = "Joe", lastName = "Bloggs",
        needsAdjustment = true, fsbScoresAndFeedbackSubmitted = false, fsacIndicator = fsacIndicator, dateReady = OffsetDateTime.now)
      when(mockCandidateAllocationService.findCandidatesEligibleForEventAllocation(any[String], any[EventType], any[String]))
        .thenReturnAsync(CandidatesEligibleForEventResponse(List(candidate), 1))

      val result = controller.findCandidatesEligibleForEventAllocation(location, eventType, description)(
        findCandidatesEligibleForEventAllocationRequest(location, eventType, description)).run()
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "candidates").as[List[CandidateEligibleForEvent]] mustBe List(candidate)
      (jsonResponse \ "totalCandidates").as[Int] mustBe 1

      status(result) mustBe OK
    }
  }

  "Allocate candidates" must {
    "return CONFLICT when there is a OptimisticLockException" in new TestFixture {
      when(mockCandidateAllocationService.allocateCandidates(any[model.command.CandidateAllocations], any[Boolean])(
        any[HeaderCarrier], any[RequestHeader]))
        .thenReturn(Future.failed(OptimisticLockException("Boom")))

      val allocations = CandidateAllocations(version = None,
        allocations = Seq(CandidateAllocation("appId", AllocationStatuses.CONFIRMED, removeReason = None))
      )
      val request = fakeRequest(allocations)
      val result = controller.allocateCandidates("eventId", "sessionId", append = false)(request)
      status(result) mustBe CONFLICT
    }

    "return NOT_ACCEPTABLE when there is a CandidateAlreadyAssignedToOtherEventException" in new TestFixture {
      when(mockCandidateAllocationService.allocateCandidates(any[model.command.CandidateAllocations], any[Boolean])(
        any[HeaderCarrier], any[RequestHeader]))
        .thenReturn(Future.failed(CandidateAlreadyAssignedToOtherEventException("Boom")))

      val allocations = CandidateAllocations(version = None,
        allocations = Seq(CandidateAllocation("appId", AllocationStatuses.CONFIRMED, removeReason = None))
      )
      val request = fakeRequest(allocations)
      val result = controller.allocateCandidates("eventId", "sessionId", append = false)(request)
      status(result) mustBe NOT_ACCEPTABLE
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockCandidateAllocationService = mock[CandidateAllocationService]
    val MockVenue = Venue("London FSAC", "Bush House")
    val MockLocation = Location("London")

    val controller = new CandidateAllocationController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockCandidateAllocationService
    )
  }

  def findCandidatesEligibleForEventAllocationRequest(location: String, eventType: EventType, t: String) = {
    FakeRequest(Helpers.GET,
      controllers.routes.CandidateAllocationController.findCandidatesEligibleForEventAllocation(location, eventType, t).url,
      FakeHeaders(), "").withHeaders("Content-Type" -> "application/json")
  }
}
