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
import model.OnlineTestCommands.OnlineTestApplication
import model.command.ResetOnlineTest
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.Helpers._
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.ResetPhase2Test.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.{ Phase1TestService, Phase2TestService, Phase3TestService }
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

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
}
