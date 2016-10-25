/*
 * Copyright 2016 HM Revenue & Customs
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
import model.Exceptions.ApplicationNotFound
import model.PersistedObjects.Implicits._
import model.PersistedObjects.{ ApplicationProgressStatuses, ApplicationUser }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsArray, JsValue, Json }
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.DiagnosticReportingRepository

import scala.concurrent.Future

class DiagnosticReportControllerSpec extends PlaySpec with Results with MockitoSugar {

  val mockdiagnosticReportRepository = mock[DiagnosticReportingRepository]

  "Get application by id" should {
    "return all non-sensitive information about the user application" in new TestFixture {
      val expectedApplications = List(Json.obj("applicationId" -> "app1", "userId" -> "user1", "frameworkId" -> "FastStream-2016"))
      when(mockdiagnosticReportRepository.findByUserId("user1")).thenReturn(Future.successful(expectedApplications))
      val result = TestableDiagnosticReportingController.getApplicationById("user1")(createGetUserByIdRequest(
        "user1"
      )).run

      val resultJson = contentAsJson(result)

      val actualApplications = resultJson.as[JsValue]
      status(result) must be(200)
      resultJson mustBe JsArray(expectedApplications)
    }

    "return NotFound if the user cannot be found" in new TestFixture {
      val IncorrectUserId = "1234"
      when(mockdiagnosticReportRepository.findByUserId(IncorrectUserId)).thenReturn(Future.failed(
        new ApplicationNotFound(IncorrectUserId)
      ))
      val result = TestableDiagnosticReportingController.getApplicationById(IncorrectUserId)(createGetUserByIdRequest(IncorrectUserId)).run

      status(result) must be(NOT_FOUND)
    }
  }

  "All users report" should {
    "return all non-sensitive information about the users" in new TestFixture {
      val expectedApplications = List(
        Json.obj("applicationId" -> "app1", "userId" -> "user1", "frameworkId" -> "FastStream-2016"),
        Json.obj("applicationId" -> "app2", "userId" -> "user2", "frameworkId" -> "EDIP-2016")
      )
      when(mockdiagnosticReportRepository.findAll()).thenReturn(Future.successful(expectedApplications))
      val result = TestableDiagnosticReportingController.getAllApplications()(createGetAllUsersRequest).run

      val resultJson = contentAsJson(result)

      val actualApplications = resultJson.as[JsValue]
      status(result) must be(200)
      resultJson mustBe JsArray(expectedApplications)
    }

    "return an empty list if there are no users" in new TestFixture {
      when(mockdiagnosticReportRepository.findAll()).thenReturn(Future.successful(Nil))
      val result = TestableDiagnosticReportingController.getAllApplications()(createGetAllUsersRequest).run

      val resultJson = contentAsJson(result)
      val actualApplications = resultJson.as[JsValue]
      status(result) mustBe 200
      resultJson mustBe JsArray()
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestableDiagnosticReportingController extends DiagnosticReportController {
      val drRepository = mockdiagnosticReportRepository
    }

    def createGetUserByIdRequest(userId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getApplicationById(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createGetAllUsersRequest() = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getAllApplications().url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
