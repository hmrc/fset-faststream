/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{any => _}
import connectors.exchange.UserResponse
import helpers.NotificationType.{apply => _}
import models.ApplicationRoute._
import models._
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ConsiderForSdipControllerSpec extends BaseControllerSpec {

  "present" should {
    "display dashboard with sdip eligibility info when faststream application is not submitted" in new TestFixture {
      val result = controller(currentCandidateWithApp).present()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display dashboard with sdip eligibility info when faststream application is withdrawn" in new TestFixture {
      val withdrawnApplication = currentCandidateWithApp.copy(application = CachedDataExample.WithdrawApplication)

      val result = controller(withdrawnApplication).present()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display dashboard with sdip eligibility info when faststream phase1 tests are expired" in new TestFixture {
      val phase1TestsExpiredCandidate = currentCandidateWithApp.copy(application = CachedDataExample.Phase1TestsExpiredApplication)

      val result = controller(phase1TestsExpiredCandidate).present()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display warning message when faststream application is not submitted" in new TestFixture {
      val result = controller(currentCandidateWithApp).present()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(true).url)
    }

    "display consider me for sdip page when faststream application is submitted" in new TestFixture {
      val submittedCandidate = currentCandidateWithApp.copy(application = CachedDataExample.SubmittedApplication)

      val result = controller(submittedCandidate).present()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("I agree and want to be considered for SDIP")
    }

    "display dashboard with no ability to continue the faststream application as SDIP " +
      "when creating new SDIP accounts is blocked" in new TestFixture {
      val submittedCandidate = currentCandidateWithApp.copy(application = CachedDataExample.CreatedApplication)

      val newAccountsBlockedAppRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = Some(LocalDateTime.now)
      }

      val result = controller(candWithApp = submittedCandidate, appRouteState = newAccountsBlockedAppRouteState)
        .present()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present(false).url)
    }
  }

  "continue as sdip" should {
    "display sdip dashboard with success message" in new TestFixture {
      val archiveEmail = ConsiderMeForSdipHelper.convertToArchiveEmail(currentCandidateWithApp.user.email)
      when(mockUserManagementClient.register(eqTo(archiveEmail), any[String],
        eqTo(currentCandidateWithApp.user.firstName), eqTo(currentCandidateWithApp.user.lastName))(any[HeaderCarrier]))
        .thenReturn(Future.successful(
          UserResponse("", "", None, isActive = false, UniqueIdentifier(UUID.randomUUID()), "", disabled=false, "", Nil, "", None)))
      when(mockApplicationClient.continueAsSdip(any[UniqueIdentifier], any[UniqueIdentifier])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier],
        any[Request[_]])).thenReturn(Future.successful(currentCandidate))

      val result = controller().continueAsSdip()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.present().url)
      flash(result).data mustBe Map("success" -> "faststream.continueAsSdip.success")
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
      def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState) = {
      new ConsiderForSdipController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent,
      mockNotificationTypeHelper, mockApplicationClient, mockUserManagementClient) with TestableSecureActions {
        override val candidate: CachedData = CachedData(candWithApp.user, Some(candWithApp.application))
        override val candidateWithApp: CachedDataWithApp = candWithApp
        override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      }
    }
  }
}
