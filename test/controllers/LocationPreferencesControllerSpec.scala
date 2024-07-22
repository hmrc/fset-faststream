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

import model.Exceptions.{CannotUpdateLocationPreferences, LocationPreferencesNotFound}
import model.{LocationId, SelectedLocations}
import org.mockito.Mockito._
import play.api.test.Helpers._
import services.location.LocationPreferencesService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class LocationPreferencesControllerSpec extends UnitWithAppSpec {
  val mockLocationPreferencesService = mock[LocationPreferencesService]

  val controller = new LocationPreferencesController(
    stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
    mockLocationPreferencesService
  )

  val twoLocations = SelectedLocations(List(LocationId("location1"), LocationId("location2")), List("Interest 1"))

  "find preferences" should {
    "return location preferences" in {
      when(mockLocationPreferencesService.find(AppId)).thenReturn(Future.successful(twoLocations))

      val response = controller.find(AppId)(fakeRequest)

      val selectedLocations = contentAsJson(response).as[SelectedLocations]
      selectedLocations mustBe twoLocations
    }

    "return not found when selected locations do not exist" in {
      when(mockLocationPreferencesService.find(AppId)).thenReturn(Future.failed(LocationPreferencesNotFound(AppId)))

      val response = controller.find(AppId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
  }

  "update preferences" should {
    val Request = fakeRequest(twoLocations)

    "store a new location preference" in {
      when(mockLocationPreferencesService.update(AppId, twoLocations)).thenReturn(emptyFuture)
      val response = controller.update(AppId)(Request)
      status(response) mustBe OK
    }

    "return bad request when update fails" in {
      when(mockLocationPreferencesService.update(AppId, twoLocations)).thenReturn(Future.failed(CannotUpdateLocationPreferences(AppId)))
      val response = controller.update(AppId)(Request)
      status(response) mustBe BAD_REQUEST
    }
  }
}
