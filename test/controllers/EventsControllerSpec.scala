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

package controllers

import config.TestFixtureBase
import model.persisted.eventschedules.Event
import model.persisted.eventschedules.EventType
import model.persisted.eventschedules.VenueType
import org.joda.time.{ LocalDate, LocalTime }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import services.events.EventsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class EventsControllerSpec extends UnitWithAppSpec {

  "Upload assessment events" should {
    "return CREATED with valid input" in new TestFixture {
      when(mockEventsService.saveAssessmentEvents()).thenReturn(Future.successful(unit))
      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe CREATED
    }

    "return UNPROCESSABLE_ENTITY when parsing goes wrong" in new TestFixture {
      when(mockEventsService.saveAssessmentEvents()).thenReturn(Future.failed(new Exception()))

      val res = controller.saveAssessmentEvents()(FakeRequest())
      status(res) mustBe UNPROCESSABLE_ENTITY
    }

    "return OK with all events" in new TestFixture {
      when(mockEventsService.fetchEvents(any[EventType.EventType], any[VenueType.VenueType])).thenReturn(Future.successful(
        List(
          Event("id", EventType.FSAC, "London", VenueType.LONDON_FSAC, LocalDate.now, 32, 10, 5, LocalTime.now, LocalTime.now, Map.empty)
        )
      ))
      val res = controller.fetchEvents("FSAC","LONDON_FSAC")(FakeRequest())
      status(res) mustBe OK
    }

     "return 400 for invalid event or venue types" in new TestFixture {
       status(controller.fetchEvents("blah","LONDON_FSAC")(FakeRequest())) mustBe BAD_REQUEST
       status(controller.fetchEvents("FSAC","blah")(FakeRequest())) mustBe BAD_REQUEST
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockEventsService = mock[EventsService]
    val controller = new EventsController {
      val eventsService = mockEventsService
    }
  }
}
