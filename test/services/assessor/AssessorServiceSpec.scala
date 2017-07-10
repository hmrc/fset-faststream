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

import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import services.BaseServiceSpec
import services.assessoravailability.AssessorService
import org.mockito.ArgumentMatchers._

import scala.concurrent.duration._
import model.Exceptions
import model.Exceptions.AssessorNotFoundException
import model.persisted.{ AssessorAllocation, EventExamples }
import model.persisted.eventschedules.{ Location, Venue }
import model.persisted.assessor.AssessorExamples._
import repositories.{ AllocationRepository, AssessorRepository }
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }

import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

class AssessorServiceSpec extends BaseServiceSpec {

  "save assessor" must {

    "save NEW assessor when assessor is new" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(None))
      when(mockAssessorRepository.save(eqTo(AssessorNew))).thenReturn(Future.successful(()))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorNew))
    }

    "update skills and do not update availability when assessor previously EXISTED" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(Some(AssessorExisting)))
      when(mockAssessorRepository.save(eqTo(AssessorMerged))).thenReturn(Future.successful(()))
      val response = service.saveAssessor(AssessorUserId, model.exchange.Assessor.apply(AssessorNew)).futureValue
      response mustBe unit
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorMerged))
    }
  }

  "add availability" must {

    "throw assessor not found exception when assessor cannot be found" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(None))

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)

      intercept[AssessorNotFoundException] {
        Await.result(service.addAvailability(AssessorUserId, exchangeAvailability), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "merge availability to EXISTING assessor" in new TestFixture {

      when(mockAssessorRepository.find(eqTo(AssessorUserId))).thenReturn(Future.successful(Some(AssessorExisting)))
      when(mockAssessorRepository.save(eqTo(AssessorWithAvailabilityMerged))).thenReturn(Future.successful(()))
      when(mockLocationsWithVenuesRepo.location(any[String])).thenReturn(Future.successful(EventExamples.LocationLondon))

      val exchangeAvailability = AssessorWithAvailability.availability.map(model.exchange.AssessorAvailability.apply)

      val result = service.addAvailability(AssessorUserId, exchangeAvailability).futureValue

      result mustBe unit

      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
      verify(mockAssessorRepository).save(eqTo(AssessorWithAvailabilityMerged))
    }
  }


  "find assessor" must {
    "return assessor details" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(Some(AssessorExisting)))

      val response = service.findAssessor(AssessorUserId).futureValue

      response mustBe model.exchange.Assessor(AssessorExisting)
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there is no assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(None))

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAssessor(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  "find assessor availability" should {

    "return assessor availability" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(Some(AssessorWithAvailability)))

      val response = service.findAvailability(AssessorUserId).futureValue

      val expected = AssessorWithAvailability.availability.map { a => model.exchange.AssessorAvailability.apply(a)}

      response mustBe expected
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }

    "throw exception when there are is assessor" in new TestFixture {
      when(mockAssessorRepository.find(AssessorUserId)).thenReturn(Future.successful(None))

      intercept[Exceptions.AssessorNotFoundException] {
        Await.result(service.findAvailability(AssessorUserId), 10 seconds)
      }
      verify(mockAssessorRepository).find(eqTo(AssessorUserId))
    }
  }

  trait TestFixture {
    val mockAssessorRepository = mock[AssessorRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val mockAllocationRepo = mock[AllocationRepository[model.persisted.AssessorAllocation]]
    val mockEventRepo = mock[EventsRepository]

    when(mockLocationsWithVenuesRepo.venues).thenReturn(Future.successful(
      Set(Venue("london fsac", "bush house"), Venue("virtual", "virtual venue"))
    ))
    when(mockLocationsWithVenuesRepo.locations).thenReturn(Future.successful(
      Set(Location("London"))
    ))
    val service = new AssessorService {
      val assessorRepository: AssessorRepository = mockAssessorRepository
      val allocationRepo: AllocationRepository[AssessorAllocation] = mockAllocationRepo
      val eventsRepo: EventsRepository = mockEventRepo
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }
}
