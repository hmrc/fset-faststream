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

import java.time.LocalDateTime
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{ any => _ }
import config.{ CSRCache, CSRHttp }
import connectors.exchange.UserResponse
import connectors.{ ApplicationClient, UserManagementClient }
import helpers.NotificationType.{ apply => _ }
import models.ApplicationRoute._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.Helpers._
import security.UserService
import testkit.BaseControllerSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class ConsiderForSdipControllerSpec extends BaseControllerSpec {

  "present" should {
    "display warning message when application route is not faststream" in new TestFixture {
      val result = controller(currentCandidateWithEdipApp).present()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present().url)
      flash(result).data mustBe Map("warning" -> "Sorry, you don't have a Civil Service Fast Stream application")
    }

    "display dashboard with sdip eligibility info when faststream application is not submitted" in new TestFixture {
      val result = controller(currentCandidateWithApp).present()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display dashboard with sdip eligibility info when faststream application is withdrawn" in new TestFixture {
      val withdrawnApplication = currentCandidateWithApp.copy(application = CachedDataExample.WithdrawApplication)
      val result = controller(withdrawnApplication).present()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display dashboard with sdip eligibility info when faststream phase1 tests are expired" in new TestFixture {
      val phase1TestsExpiredCandidate = currentCandidateWithApp.copy(application = CachedDataExample.Phase1TestsExpiredApplication)
      val result = controller(phase1TestsExpiredCandidate).present()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display warning message when faststream application is not submitted" in new TestFixture {
      val result = controller(currentCandidateWithApp).present()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display consider me for sdip page when faststream application is submitted" in new TestFixture {
      val submittedCandidate = currentCandidateWithApp.copy(application = CachedDataExample.SubmittedApplication)
      val result = controller(submittedCandidate).present()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe OK
      content must include("I agree and want to be considered for SDIP")
    }
  }

  "continue as sdip" should {
    "display sdip dashboard with success message" in new TestFixture {
      val archiveEmail = ConsiderMeForSdipHelper.convertToArchiveEmail(currentCandidateWithApp.user.email)
      when(mockUserManagementClient.register(eqTo(archiveEmail), any[String],
        eqTo(currentCandidateWithApp.user.firstName), eqTo(currentCandidateWithApp.user.lastName))(any[HeaderCarrier]))
        .thenReturn(Future.successful(UserResponse("", "", None, isActive = false, UniqueIdentifier(UUID.randomUUID()), "", "", "", "")))
      when(mockApplicationClient.continueAsSdip(any[UniqueIdentifier], any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(currentCandidate))

      val result = controller().continueAsSdip()(fakeRequest)
      val content = contentAsString(result)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present().url)
      flash(result).data mustBe Map("success" -> "You'll now be considered for the Summer Diversity Internship Programme only.")
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockUserService = mock[UserService]
    val mockUserManagementClient = mock[UserManagementClient]

    class TestableConsiderForSdipController extends ConsiderForSdipController(mockApplicationClient, mockCacheClient, mockUserManagementClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = securityEnvironment
      val appRouteConfigMap = Map.empty[ApplicationRoute, ApplicationRouteState]
      when(securityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller(implicit candidateWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState) = new TestableConsiderForSdipController {
      override val Candidate: CachedData = CachedData(candidateWithApp.user, Some(candidateWithApp.application))
      override val CandidateWithApp: CachedDataWithApp = candidateWithApp
      override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
    }

    def defaultApplicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = Some(LocalDateTime.now)
    }
  }
}
