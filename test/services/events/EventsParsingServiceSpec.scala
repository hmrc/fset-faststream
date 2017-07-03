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

package services.events

import model.persisted.eventschedules.{ Event, EventType, Location, Venue }
import repositories.events.LocationsWithVenuesRepository
import services.BaseServiceSpec
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

import scala.concurrent.Future
import scala.util.Success

class EventsParsingServiceSpec extends BaseServiceSpec {
  "processCentres" must {
    "successfully saves and loads the file contents" in new GoodTestFixture {
      when(mockLocationsWithVenuesRepo.venue(any[String])).thenReturn(Future.successful(Venue("london fsac", "bush house")))
      when(mockLocationsWithVenuesRepo.location(any[String])).thenReturn(Future.successful(Location("London")))
      val events: Seq[Event] = service.processCentres().futureValue

      events.size mustBe 2
      events.head.eventType mustBe EventType.FSAC
      events(1).skillRequirements.getOrElse("QUALITY_ASSURANCE_COORDINATOR", "--") mustBe 0
    }

    "fails gracefully when any field is malformed" in new MalformedTestFixture {
      an[Exception] must be thrownBy service.processCentres().futureValue
    }

    "fails gracefully when event description is longer than 10 characters" in new LongEventDescription {
      an[Exception] must be thrownBy service.processCentres().futureValue
    }
  }

  trait MalformedTestFixture {
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val service = new EventsParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "fsac,PDFS FSB,london,london fsac,03/04/17,09:0,12:00,36,4,5,6,1,1,1,1,2", // malformed starttime
        "telephone interview,ORAC,london,virtual,,08:00,13:30,36,24,7,,,,,," // missing date
      ))
      def locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }

  trait GoodTestFixture {
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val service = new EventsParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "fsac,PDFS FSB,London,london fsac,03/04/17,09:00,12:00,36,4,5,6,1,1,1,1,2",
        "telephone interview,ORAC,London,virtual,04/04/17,08:00,13:30,36,24,7,,,,,,"
      ))
      def locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }

  trait LongEventDescription {
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val service = new EventsParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "Skype Interview,long event description,London,london fsac,03/04/17,09:00,12:00,36,4,5,6,1,1,1,1,2"
      ))

      def locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }

}
