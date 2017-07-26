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

import model.Exceptions.AssessorNotFoundException
import model.exchange.{ AssessorAvailabilities, UpdateAllocationStatusRequest }
import model.persisted.assessor.Assessor
import model.persisted.assessor.AssessorExamples._
import model.persisted.eventschedules.Venue
import model.persisted.{ AssessorAllocation, EventExamples, ReferenceData }
import model.{ AllocationStatuses, Exceptions }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }
import repositories.{ AllocationRepository, AssessorRepository }
import services.BaseServiceSpec
import services.assessoravailability.AssessorService
import testkit.MockitoImplicits._

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

class AssessorServiceSpec extends BaseServiceSpec {

  "save assessor" must {

    "save NEW assessor when assessor is new" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(None)
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(any[Assessor])
    }

    "update skills and do not update availability when assessor previously EXISTED" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturnAsync(Some(AssessorExisting))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
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
  }

  trait TestFixture {
    val mockAssessorRepository = mock[AssessorRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val mockAllocationRepo = mock[AllocationRepository[model.persisted.AssessorAllocation]]
    val mockEventRepo = mock[EventsRepository]
    val virtualVenue = Venue("virtual", "virtual venue")
    val venues = ReferenceData(List(Venue("london fsac", "bush house"), virtualVenue), virtualVenue, virtualVenue)

    when(mockLocationsWithVenuesRepo.venues).thenReturnAsync(venues)
    when(mockAssessorRepository.save(any[Assessor])).thenReturnAsync()

    val service = new AssessorService {
      val assessorRepository: AssessorRepository = mockAssessorRepository
      val allocationRepo: AllocationRepository[AssessorAllocation] = mockAllocationRepo
      val eventsRepo: EventsRepository = mockEventRepo
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }

}
