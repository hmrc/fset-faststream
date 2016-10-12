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

  val mockSecretReportRepository = mock[DiagnosticReportingRepository]

  "Get user by id" should {
    "return all information about the user" in new TestFixture {
      val expectedApplications = List(Json.obj("applicationId" -> "app1", "userId" -> "user1", "frameworkId" -> "FastStream-2016"))
      when(mockSecretReportRepository.findByUserId("user1")).thenReturn(Future.successful(expectedApplications))
      val result = TestableSecretReportingController.getUserById("user1")(createOnlineTestRequest(
        "user1"
      )).run

      val resultJson = contentAsJson(result)

      val actualApplications = resultJson.as[JsValue]
      status(result) must be(200)
      resultJson mustBe JsArray(expectedApplications)
    }

    "return NotFound if the user cannot be found" in new TestFixture {
      val IncorrectUserId = "1234"
      when(mockSecretReportRepository.findByUserId(IncorrectUserId)).thenReturn(Future.failed(
        new ApplicationNotFound(IncorrectUserId)
      ))
      val result = TestableSecretReportingController.getUserById(IncorrectUserId)(createOnlineTestRequest(IncorrectUserId)).run

      status(result) must be(NOT_FOUND)
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestableSecretReportingController extends DiagnosticReportController {
      val drRepository = mockSecretReportRepository
    }

    def createOnlineTestRequest(userId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getUserById(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
