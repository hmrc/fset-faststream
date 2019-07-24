/*
 * Copyright 2019 HM Revenue & Customs
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

import model.ApplicationStatus
import model.Exceptions.{ ContactDetailsNotFoundForEmail, ExpiredTestForTokenException }
import model.OnlineTestCommands.OnlineTestApplication
import model.command.{ InvigilatedTestUrl, ResetOnlineTest, ResetOnlineTest2, VerifyAccessCode }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.phase1.{ Phase1TestService, Phase1TestService2 }
import services.onlinetesting.phase2.{ Phase2TestService, Phase2TestService2 }
import services.onlinetesting.Exceptions.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.phase3.Phase3TestService
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class OnlineTestsControllerSpec extends UnitWithAppSpec {

  val mockPhase1TestService = mock[Phase1TestService]
  val mockPhase1TestService2 = mock[Phase1TestService2]
  val mockPhase2TestService = mock[Phase2TestService]
  val mockPhase2TestService2 = mock[Phase2TestService2]
  val mockPhase3TestService = mock[Phase3TestService]
  val mockApplicationRepository = mock[GeneralApplicationRepository]
  val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = "userId",
    testAccountId = "testAccountId",
    guaranteedInterview = false,
    needsOnlineAdjustments = false,
    needsAtVenueAdjustments = false,
    preferredName = "Optimus",
    lastName = "Prime",
    eTrayAdjustments = None,
    videoInterviewAdjustments = None
  )

  def controller = new OnlineTestController {
    val phase1TestService = mockPhase1TestService
    val phase1TestService2 = mockPhase1TestService2
    val phase2TestService = mockPhase2TestService
    val phase2TestService2 = mockPhase2TestService2
    val phase3TestService = mockPhase3TestService
    val appRepository = mockApplicationRepository
  }

  "reset phase1 tests" should {
    "return OK when candidate is reset" in {
      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))

      when(mockPhase1TestService2.resetTest(any[OnlineTestApplication], any[String], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val response = controller.resetPhase1OnlineTests(AppId)(fakeRequest(ResetOnlineTest2("appId", "orderId", "")))
      status(response) mustBe OK
    }

    "return NOT_FOUND when candidate is not found" in {
      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(None))

      val response = controller.resetPhase1OnlineTests(AppId)(fakeRequest(ResetOnlineTest2("appId", "orderId", "")))
      status(response) mustBe NOT_FOUND
    }
  }

  "reset phase2 tests" should {
    "register user with new test" in {
      when(mockPhase2TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))
      val response = controller.resetPhase2OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe OK
    }

    "return the response as reset limit exceeded" in {
      when(mockPhase2TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ResetLimitExceededException()))

      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))
      val response = controller.resetPhase2OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe LOCKED
    }

    "return cannot reset phase2 tests exception" in {
      when(mockPhase2TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(CannotResetPhase2Tests()))

      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))
      val response = controller.resetPhase2OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe NOT_FOUND
    }

    "return not found exception" in {
      when(mockPhase2TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ResetLimitExceededException()))

      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(None))
      val response = controller.resetPhase2OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe NOT_FOUND
    }
  }

  "reset phase3 tests" should {
    "return OK when candidate is reset" in {
      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))

      when(mockPhase3TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val response = controller.resetPhase3OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe OK
    }

    "return CONFLICT when test cannot be reset" in {
      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTestApplication)))

      when(mockPhase3TestService.resetTests(any[OnlineTestApplication], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(CannotResetPhase3Tests()))

      val response = controller.resetPhase3OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe CONFLICT
    }

    "return NOT_FOUND when candidate is not found" in {
      when(mockApplicationRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(None))

      val response = controller.resetPhase3OnlineTest(AppId)(fakeRequest(ResetOnlineTest(Nil, "")))
      status(response) mustBe NOT_FOUND
    }
  }

  "verify access code" should {
    "return an invigilated test url when the supplied email and access code belong to a valid user" in {
      val verifyAccessCode = VerifyAccessCode(email = "test@email.com", accessCode = "ACCESS-CODE")
      val jsonString = Json.toJson(verifyAccessCode).toString()

      val json: JsValue = Json.parse(jsonString)
      val fakeRequest = verifyAccessCodeRequest(json)

      val invigilatedTestUrl = "invigilated.test.url"
      when(mockPhase2TestService.verifyAccessCode(any[String], any[String]))
        .thenReturn(Future.successful(invigilatedTestUrl))

      val response = controller.verifyAccessCode()(fakeRequest)
      status(response) mustBe OK

      val invigilatedTestUrlReturned = contentAsJson(response).as[InvigilatedTestUrl]
      invigilatedTestUrlReturned mustBe InvigilatedTestUrl(invigilatedTestUrl)
    }

    "return NOT FOUND when the supplied email and access code do not belong to a valid user" in {
      val verifyAccessCode = VerifyAccessCode(email = "test@email.com", accessCode = "ACCESS-CODE")
      val jsonString = Json.toJson(verifyAccessCode).toString()

      val json: JsValue = Json.parse(jsonString)
      val fakeRequest = verifyAccessCodeRequest(json)
      val noUserFound = Future.failed(ContactDetailsNotFoundForEmail())

      when(mockPhase2TestService.verifyAccessCode(any[String], any[String])).thenReturn(noUserFound)

      val response = controller.verifyAccessCode()(fakeRequest)
      status(response) mustBe NOT_FOUND
    }

    "return FORBIDDEN when the test is expired" in {
      val verifyAccessCode = VerifyAccessCode(email = "test@email.com", accessCode = "ACCESS-CODE")
      val jsonString = Json.toJson(verifyAccessCode).toString()

      val json: JsValue = Json.parse(jsonString)
      val fakeRequest = verifyAccessCodeRequest(json)
      val tokenExpired = Future.failed(ExpiredTestForTokenException(""))

      when(mockPhase2TestService.verifyAccessCode(any[String], any[String])).thenReturn(tokenExpired)

      val response = controller.verifyAccessCode()(fakeRequest)
      status(response) mustBe FORBIDDEN
    }
  }

  private def verifyAccessCodeRequest(json: JsValue) = {
    FakeRequest(
      Helpers.POST,
      controllers.routes.OnlineTestController.verifyAccessCode().url, FakeHeaders(), json
    ).withHeaders("Content-Type" -> "application/json")
  }
}
