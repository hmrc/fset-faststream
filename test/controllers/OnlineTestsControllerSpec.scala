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

import model.ApplicationStatus
import model.Exceptions.{ ContactDetailsNotFoundForEmail, ExpiredTestForTokenException }
import model.OnlineTestCommands.OnlineTestApplication
import model.command.{ InvigilatedTestUrl, ResetOnlineTest, VerifyAccessCode }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.ResetPhase2Test.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.{ Phase1TestService, Phase2TestService, Phase3TestService }
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{ Failure, Success }

class OnlineTestsControllerSpec extends UnitWithAppSpec {
  val mockPhase1TestService = mock[Phase1TestService]
  val mockPhase2TestService = mock[Phase2TestService]
  val mockPhase3TestService = mock[Phase3TestService]
  val mockApplicationRepository = mock[GeneralApplicationRepository]
  val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = "userId",
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
    val phase2TestService = mockPhase2TestService
    val phase3TestService = mockPhase3TestService
    val appRepository = mockApplicationRepository
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
