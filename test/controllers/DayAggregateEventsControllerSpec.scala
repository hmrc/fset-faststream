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
import model.persisted.EventExamples
import model.persisted.eventschedules.Event
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class DayAggregateEventsControllerSpec extends UnitWithAppSpec {

  val MySkills = List("QAC", "CHAIR")

  "find" should {

    "returns day aggregated events when search by skills" in new TestFixture {
      when(mockEventsRepo.getEvents(None, None, None, Some(MySkills)))
        .thenReturn(Future.successful(EventExamples.EventsNew))
      val res = controller.findBySkillTypes(MySkills.mkString(","))(FakeRequest())
      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal must contain theSameElementsAs EventExamples.DayAggregateEventsNew
    }

    "returns day aggregated events when search by location and skills" in new TestFixture {
      val location = EventExamples.LocationLondon
      when(mockLocationsWithVenuesRepo.location(location.name)).thenReturn(Future.successful(location))
      when(mockEventsRepo.getEvents(None, None, Some(location), Some(MySkills)))
        .thenReturn(Future.successful(EventExamples.EventsNew.filter(_.location == location)))

      val res = controller.findBySkillTypesAndLocation(MySkills.mkString(","), location.name)(FakeRequest())

      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal must contain theSameElementsAs EventExamples.DayAggregateEventsNew.filter(_.location == location)
    }

    "return EMPTY list when nothing found" in new TestFixture {
      when(mockEventsRepo.getEvents(None, None, None, Some(MySkills)))
        .thenReturn(Future.successful(List.empty[Event]))
      val res = controller.findBySkillTypes(MySkills.mkString(","))(FakeRequest())
      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal mustBe List.empty[DayAggregateEvent]
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockEventsRepo: EventsRepository = mock[EventsRepository]
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val controller = new DayAggregateEventController {
      val eventsRepository: EventsRepository = mockEventsRepo
      val locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
    }
  }
}
