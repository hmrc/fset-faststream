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

import models.ApplicationRoute._
import models.SecurityUserExamples._
import models.{CachedDataExample, CachedDataWithApp, ProgressResponseExamples}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SubmitApplicationControllerSpec extends BaseControllerSpec {

  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))

  "present submit" should {
    "display submit application page when application submission is enabled" in new TestFixture {
      val result = controller(currentCandidateWithEdipApp, applicationRouteStateAllEnabled).presentSubmit()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("Submit application")
    }
    "redirect to home page when application submission is disabled" in new TestFixture {
      val result = controller(currentCandidateWithEdipApp, applicationRouteStateSubmissionNotEnabled).presentSubmit()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }

  "submit" should {
    "redirect to submitted page" in new TestFixture {
      when(mockApplicationClient.submitApplication(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InPreview))

      val result = controller(currentCandidateWithEdipApp, applicationRouteStateAllEnabled).submit()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SubmitApplicationController.presentSubmitted().url))
      verify(mockApplicationClient).submitApplication(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier])
    }
    "redirect to home page" in new TestFixture {
      val result = controller(currentCandidateWithEdipApp, applicationRouteStateSubmissionAndAccountsDisabled).submit()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val applicationRouteStateAllEnabled = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None
    }

    val applicationRouteStateSubmissionNotEnabled = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = false
      val applicationsStartDate = None
    }

    val applicationRouteStateSubmissionAndAccountsDisabled = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = false
      val applicationsSubmitEnabled = false
      val applicationsStartDate = None
    }

    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
      appRouteConfig: ApplicationRouteState) = {
      val appRouteConfigMap = Map(Faststream -> appRouteConfig, Edip -> appRouteConfig, Sdip -> appRouteConfig)
      when(mockConfig.applicationRoutesFrontend).thenReturn(appRouteConfigMap)
      new SubmitApplicationController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper,
        mockApplicationClient) with TestableSecureActions {
        override val candidateWithApp: CachedDataWithApp = candWithApp
      }
    }
  }
}

