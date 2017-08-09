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
import mocks.application.DocumentRootInMemoryRepository
import model.EvaluationResults.Green
import model.command.WithdrawApplication
import model.exchange.{ CandidateEligibleForEvent, CandidatesEligibleForEventResponse }
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, SchemeId }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import repositories.fileupload.FileUploadMongoRepository
import services.AuditService
import services.application.ApplicationService
import services.assessmentcentre.AssessmentCentreService
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class ApplicationControllerSpec extends UnitWithAppSpec {

  val ApplicationId = "1111-1111"
  val aWithdrawApplicationRequest = WithdrawApplication("Something", Some("Else"), "Candidate")
  val auditDetails = Map("applicationId" -> ApplicationId, "withdrawRequest" -> aWithdrawApplicationRequest.toString)

  "Create Application" must {
    "create an application" in new TestFixture {
      val result = TestApplicationController.createApplication(createApplicationRequest(
        s"""
           |{
           |  "userId":"1234",
           |  "frameworkId":"FASTSTREAM-2016",
           |  "applicationRoute":"${ApplicationRoute.Faststream}"
           |}
        """.stripMargin
      ))
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationStatus").as[String] mustBe "CREATED"
      (jsonResponse \ "userId").as[String] mustBe "1234"

      verify(mockAuditService).logEvent(eqTo("ApplicationCreated"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return a system error on invalid json" in new TestFixture {
      val result =
        TestApplicationController.createApplication(createApplicationRequest(
          s"""
             |{
             |  "some":"1234",
             |  "other":"FASTTRACK-2015"
             |}
          """.stripMargin
        ))

      status(result) mustBe BAD_REQUEST
    }
  }

  "Application Progress" must {
    "return the progress of an application" in new TestFixture {
      val result = TestApplicationController.applicationProgress(ApplicationId)(applicationProgressRequest(ApplicationId)).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationId").as[String] mustBe ApplicationId
      (jsonResponse \ "personalDetails").as[Boolean] mustBe true

      status(result) mustBe OK
    }

    "return a system error when applicationId doesn't exists" in new TestFixture {
      val result = TestApplicationController.applicationProgress("1111-1234")(applicationProgressRequest("1111-1234")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Find application" must {
    "return the application" in new TestFixture {
      val result = TestApplicationController.findApplication(
        "validUser",
        "validFramework"
      )(findApplicationRequest("validUser", "validFramework")).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationId").as[String] mustBe ApplicationId
      (jsonResponse \ "applicationStatus").as[String] mustBe "CREATED"
      (jsonResponse \ "userId").as[String] mustBe "validUser"

      status(result) mustBe OK
    }

    "return a system error when application doesn't exists" in new TestFixture {
      val result = TestApplicationController.findApplication(
        "invalidUser",
        "invalidFramework"
      )(findApplicationRequest("invalidUser", "invalidFramework")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Preview application" must {
    "mark the application as previewed" in new TestFixture {
      val result = TestApplicationController.preview(ApplicationId)(previewApplicationRequest(ApplicationId)(
        s"""
           |{
           |  "flag": true
           |}
        """.stripMargin
      ))
      status(result) mustBe OK
      verify(mockAuditService).logEvent(eqTo("ApplicationPreviewed"))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  "Get Scheme Results" must {
    "return the scheme results for an application" in new TestFixture {
      val resultToSave = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave, "version2", None)
      when(mockPassmarkService.getPassmarkEvaluation(any[String])).thenReturn(Future.successful(evaluation))

      val result = TestApplicationController.getPhase3Results(ApplicationId)(getPhase3ResultsRequest(ApplicationId)).run
      val jsonResponse = contentAsJson(result)

      jsonResponse mustBe Json.toJson(resultToSave)
      status(result) mustBe OK
    }

    "Return a 404 if no results are found for the application Id" in new TestFixture {
      val result = TestApplicationController.applicationProgress("1111-1234")(applicationProgressRequest("1111-1234")).run
      status(result) mustBe NOT_FOUND
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockApplicationService = mock[ApplicationService]
    val mockPassmarkService = mock[EvaluatePhase3ResultService]
    val mockAssessmentCentreService = mock[AssessmentCentreService]
    val mockFileUploadRepository = mock[FileUploadMongoRepository]

    object TestApplicationController extends ApplicationController {
      override val appRepository: GeneralApplicationRepository = DocumentRootInMemoryRepository
      override val auditService: AuditService = mockAuditService
      override val applicationService: ApplicationService = mockApplicationService
      override val passmarkService: EvaluatePhase3ResultService = mockPassmarkService
      override val assessmentCentreService: AssessmentCentreService = mockAssessmentCentreService
      override val uploadRepository: FileUploadMongoRepository = mockFileUploadRepository
    }

    def applicationProgressRequest(applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.applicationProgress(applicationId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def findApplicationRequest(userId: String, frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.findApplication(userId, frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createApplicationRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.ApplicationController.createApplication().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def previewApplicationRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.ApplicationController.preview(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def getPhase3ResultsRequest(applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.getPhase3Results(applicationId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
