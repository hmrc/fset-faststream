/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.report

import config.TestFixtureBase
import connectors.AuthProviderClient
import controllers.DiagnosticReportController
import factories.UUIDFactory
import model.Exceptions.ApplicationNotFound
import model.UniqueIdentifier
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, JsValue, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.AssessmentScoresMongoRepository
import repositories.application.DiagnosticReportingRepository
import testkit.UnitWithAppSpec
import org.mockito.ArgumentMatchers.any
import repositories.events.EventsMongoRepository
import services.assessor.AssessorService
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.concurrent.Future

class DiagnosticReportControllerSpec extends UnitWithAppSpec {
/*
  val mockDiagnosticReportRepository: DiagnosticReportingRepository = mock[DiagnosticReportingRepository]
  val mockAssessorScoresRepo: AssessmentScoresMongoRepository = mock[AssessmentScoresMongoRepository]
  val mockReviewerScoresRepo: AssessmentScoresMongoRepository = mock[AssessmentScoresMongoRepository]
  val mockAuthProvider: AuthProviderClient = mock[AuthProviderClient]
  val mockAssessorService: AssessorService = mock[AssessorService]
  val mockEventsRepo: EventsMongoRepository = mock[EventsMongoRepository]

  "Get application by id" should {
    "return all non-sensitive information about the user application" in new TestFixture {
      val appId = UUIDFactory.generateUUID()
      val expectedApplications = List(Json.obj("applicationId" -> appId, "userId" -> "user1", "frameworkId" -> "FastStream-2016"))
      when(mockDiagnosticReportRepository.findByApplicationId(appId)).thenReturnAsync(expectedApplications)
      when(mockAssessorScoresRepo.find(any[UniqueIdentifier])).thenReturnAsync(None)
      when(mockReviewerScoresRepo.find(any[UniqueIdentifier])).thenReturnAsync(None)
      val result = TestableDiagnosticReportingController.getApplicationByUserId(appId)(createGetUserByIdRequest(
        "user1"
      )).run

      val resultJson = contentAsJson(result)

      val actualApplications = resultJson.as[JsValue]
      status(result) mustBe OK
      resultJson mustBe JsArray(expectedApplications)
    }

    "return NotFound if the user cannot be found" in new TestFixture {
      val IncorrectUserId = "1234"
      when(mockDiagnosticReportRepository.findByApplicationId(IncorrectUserId)).thenReturn(Future.failed(
        ApplicationNotFound(IncorrectUserId)
      ))
      val result = TestableDiagnosticReportingController.getApplicationByUserId(IncorrectUserId)(createGetUserByIdRequest(IncorrectUserId)).run

      status(result) mustBe NOT_FOUND
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestableDiagnosticReportingController extends DiagnosticReportController {
      val drRepository: DiagnosticReportingRepository = mockDiagnosticReportRepository
      val assessorAssessmentCentreScoresRepo: AssessmentScoresMongoRepository = mockAssessorScoresRepo
      val reviewerAssessmentCentreScoresRepo: AssessmentScoresMongoRepository = mockReviewerScoresRepo
      val eventsRepo: EventsMongoRepository = mockEventsRepo

      val authProvider: AuthProviderClient = mockAuthProvider
      val assessorService: AssessorService = mockAssessorService
    }

    def createGetUserByIdRequest(userId: String): FakeRequest[String] = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getApplicationByUserId(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createGetAllUsersRequest: FakeRequest[String] = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getAllApplications().url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }*/
}
