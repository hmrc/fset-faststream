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
import model.persisted.EventExamples
import model.persisted.eventschedules.{ Event, SkillType }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.events.{ EventsRepository, LocationsWithVenuesRepository }
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class DayAggregateEventsControllerSpec extends UnitWithAppSpec {

  val MySkills = List(SkillType.QUALITY_ASSURANCE_COORDINATOR, SkillType.CHAIR)

  "find" should {
    "returns day aggregated events when search by skills" in new TestFixture {
      when(mockEventsRepo.getEvents(None, None, None, MySkills))
        .thenReturn(Future.successful(EventExamples.EventsNew))
      val res = controller.findBySkillTypes(MySkills)(FakeRequest())
      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal must contain theSameElementsAs EventExamples.DayAggregateEventsNew
    }

    "returns day aggregated events when search by location and skills" in new TestFixture {
      val location = EventExamples.LocationLondon
      when(mockLocationsWithVenuesRepo.location(location.name)).thenReturn(Future.successful(location))
      when(mockEventsRepo.getEvents(None, None, Some(location), MySkills))
        .thenReturn(Future.successful(EventExamples.EventsNew.filter(_.location == location)))

      val res = controller.findBySkillTypesAndLocation(location.name, MySkills)(FakeRequest())

      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal must contain theSameElementsAs EventExamples.DayAggregateEventsNew.filter(e => e.location == location || e.location.name == "Home")
    }

    "return EMPTY list when nothing found" in new TestFixture {
      when(mockEventsRepo.getEvents(None, None, None, MySkills))
        .thenReturn(Future.successful(List.empty[Event]))
      val res = controller.findBySkillTypes(MySkills)(FakeRequest())
      status(res) mustBe OK
      val resReal = Json.fromJson[List[DayAggregateEvent]](Json.parse(contentAsString(res))).get
      resReal mustBe List.empty[DayAggregateEvent]
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
    val mockEventsRepo              = mock[EventsRepository]
    val controller = new DayAggregateEventController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockLocationsWithVenuesRepo,
      mockEventsRepo
    )
  }
}
