/*
 * Copyright 2021 HM Revenue & Customs
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
import model.EvaluationResults.Green
import model.Exceptions.{ApplicationNotFound, CannotUpdateFSACIndicator}
import model.command.{ProgressResponse, WithdrawApplication}
import model.persisted.fileupload.FileUpload
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import model.{ApplicationResponse, ApplicationRoute, SchemeId}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import repositories.application.GeneralApplicationRepository
import repositories.fileupload.FileUploadMongoRepository
import services.application.ApplicationService
import services.assessmentcentre.AssessmentCentreService
import services.assessmentcentre.AssessmentCentreService.CandidateHasNoAnalysisExerciseException
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import services.personaldetails.PersonalDetailsService
import services.sift.ApplicationSiftService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class ApplicationControllerSpec extends UnitWithAppSpec {

  val ApplicationId = "1111-1111"
  val aWithdrawApplicationRequest = WithdrawApplication("Something", Some("Else"), "Candidate")
  val auditDetails = Map("applicationId" -> ApplicationId, "withdrawRequest" -> aWithdrawApplicationRequest.toString)

  "Create Application" must {
    "create an application" in new TestFixture {
      when(mockApplicationRepository.create(any(), any(), any())).thenReturnAsync(
        ApplicationResponse("a1234", "CREATED", ApplicationRoute.Faststream, "1234", "testAccountId",
        ProgressResponse("a1234"), None, None)
      )

      val result = testApplicationController.createApplication(createApplicationRequest(
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
        testApplicationController.createApplication(createApplicationRequest(
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
      when(mockApplicationRepository.findProgress(any())).thenReturnAsync(ProgressResponse(ApplicationId, personalDetails = true))

      val result = testApplicationController.applicationProgress(ApplicationId)(applicationProgressRequest(ApplicationId)).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationId").as[String] mustBe ApplicationId
      (jsonResponse \ "personalDetails").as[Boolean] mustBe true

      status(result) mustBe OK
    }

    "return a system error when applicationId doesn't exists" in new TestFixture {
      when(mockApplicationRepository.findProgress(any())).thenReturn(Future.failed(ApplicationNotFound("1111-1234")))

      val result = testApplicationController.applicationProgress("1111-1234")(applicationProgressRequest("1111-1234")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Find application" must {
    "return the application" in new TestFixture {
      when(mockApplicationRepository.findByUserId(any(), any())).thenReturnAsync(
        ApplicationResponse(ApplicationId, "CREATED", ApplicationRoute.Faststream, "validUser", "testAccountId",
        ProgressResponse(ApplicationId), None, None)
      )

      val result = testApplicationController.findApplication(
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
      when(mockApplicationRepository.findByUserId(any(), any())).thenReturn(Future.failed(ApplicationNotFound("invalidUser")))

      val result = testApplicationController.findApplication(
        "invalidUser",
        "invalidFramework"
      )(findApplicationRequest("invalidUser", "invalidFramework")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Preview application" must {
    "mark the application as previewed" in new TestFixture {
      when(mockApplicationRepository.preview(any())).thenReturnAsync()
      val result = testApplicationController.preview(ApplicationId)(previewApplicationRequest(ApplicationId)(
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

      val result = testApplicationController.getPhase3Results(ApplicationId)(getPhase3ResultsRequest(ApplicationId)).run
      val jsonResponse = contentAsJson(result)

      jsonResponse mustBe Json.toJson(resultToSave)
      status(result) mustBe OK
    }

    "Return a 404 if no results are found for the application Id" in new TestFixture {
      when(mockApplicationRepository.findProgress(any())).thenReturn(Future.failed(ApplicationNotFound("1111-1234")))
      val result = testApplicationController.applicationProgress("1111-1234")(applicationProgressRequest("1111-1234")).run
      status(result) mustBe NOT_FOUND
    }
  }

  "update FSAC indicator" must {
    val userId = "2222-2222"
    "successfully update when all the data is valid" in new TestFixture {
      when(mockPersonalDetailsService.updateFsacIndicator(any[String], any[String], any[String])).thenReturn(Future.successful(()))

      val request = FakeRequest(Helpers.POST,
        controllers.routes.ApplicationController.updateFsacIndicator(userId, ApplicationId, "London").url, FakeHeaders(), "")
      val result = testApplicationController.updateFsacIndicator(userId, ApplicationId, "London")(request).run
      status(result) mustBe OK
    }

    "return a bad request when the fsac indicator is invalid" in new TestFixture {
      when(mockPersonalDetailsService.updateFsacIndicator(any[String], any[String], any[String]))
        .thenReturn(Future.failed(new IllegalArgumentException("boom")))

      val request = FakeRequest(Helpers.POST,
        controllers.routes.ApplicationController.updateFsacIndicator(userId, ApplicationId, "London").url, FakeHeaders(), "")
      val result = testApplicationController.updateFsacIndicator(userId, ApplicationId, "London")(request).run
      status(result) mustBe BAD_REQUEST
    }

    "return a bad request when the repository fails to update (applicationId or userId is wrong)" in new TestFixture {
      when(mockPersonalDetailsService.updateFsacIndicator(any[String], any[String], any[String]))
        .thenReturn(Future.failed(CannotUpdateFSACIndicator("boom")))

      val request = FakeRequest(Helpers.POST,
        controllers.routes.ApplicationController.updateFsacIndicator(userId, ApplicationId, "London").url, FakeHeaders(), "")
      val result = testApplicationController.updateFsacIndicator(userId, ApplicationId, "London")(request).run
      status(result) mustBe BAD_REQUEST
    }
  }

  "Download analysis exercise" must {
    "return the streamed file contents" in new TestFixture {
      val fileId = "testFileId"
      val fileContents = "Test contents"
      val fsacTests = AssessmentCentreTests(Some(AnalysisExercise(fileId = fileId)))
      when(mockAssessmentCentreService.getTests(any[String])).thenReturnAsync(fsacTests)

      val fileUpload = FileUpload(
        id = fileId,
        contentType = "application/pdf",
        created = DateTime.now,
        fileContents = Enumerator(fileContents.toCharArray.map(_.toByte))
      )
      when(mockFileUploadRepository.retrieve(any[String])).thenReturnAsync(fileUpload)

      val result = testApplicationController.downloadAnalysisExercise(ApplicationId)(downloadAnalysisExerciseRequest(ApplicationId)).run
      status(result) mustBe OK
      contentAsString(result) mustBe fileContents
    }

    //TODO: it would be better if we explicitly returned a NOT_FOUND instead of the error bubbling up and it being handed
    // as an internal server error
    "blow up if no analysis exercise exists" in new TestFixture {
      val fsacTestsNoAnalysisExercise = AssessmentCentreTests()
      when(mockAssessmentCentreService.getTests(any[String])).thenReturnAsync(fsacTestsNoAnalysisExercise)

      val result = testApplicationController.downloadAnalysisExercise(ApplicationId)(downloadAnalysisExerciseRequest(ApplicationId)).run
      result.failed.futureValue mustBe an[CandidateHasNoAnalysisExerciseException]
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockApplicationService = mock[ApplicationService]
    val mockPassmarkService = mock[EvaluatePhase3ResultService]
    val mockApplicationSiftService = mock[ApplicationSiftService]
    val mockAssessmentCentreService = mock[AssessmentCentreService]
    val mockFileUploadRepository = mock[FileUploadMongoRepository]
    val mockPersonalDetailsService = mock[PersonalDetailsService]

    val testApplicationController = new ApplicationController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockApplicationRepository,
      mockAuditService,
      mockApplicationService,
      mockPassmarkService,
      mockApplicationSiftService,
      mockAssessmentCentreService,
      mockFileUploadRepository,
      mockPersonalDetailsService
    )

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

    def downloadAnalysisExerciseRequest(applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.downloadAnalysisExercise(applicationId).url, FakeHeaders(), "")
    }
  }
}
