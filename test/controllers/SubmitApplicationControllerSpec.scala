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

import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models.{ CachedData, CachedDataExample, CachedDataWithApp, ProgressResponseExamples }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import testkit.BaseControllerSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class SubmitApplicationControllerSpec extends BaseControllerSpec {

  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))

  "present" should {
    "display submit application page when application submission is enabled" in new TestFixture {
      val applicationRouteConfig =  new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val result = controller(currentCandidateWithEdipApp, applicationRouteConfig).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("Submit application")
    }
    "redirect to home page when application submission is disabled" in new TestFixture {
      val applicationRouteConfig =  new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val result = controller(currentCandidateWithEdipApp, applicationRouteConfig).present()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }

  "submit" should {
    "redirect to submit success page" in new TestFixture {
      val applicationRouteConfig =  new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }

      when(mockApplicationClient.submitApplication(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockApplicationClient.getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(ProgressResponseExamples.InPreview))
      when(mockUserService.save(any[CachedData])(any[HeaderCarrier])).thenReturn(Future.successful(currentCandidate))

      val result = controller(currentCandidateWithEdipApp, applicationRouteConfig).submit()(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SubmitApplicationController.success().url))
      verify(mockApplicationClient).submitApplication(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier])
      verify(mockApplicationClient).getApplicationProgress(eqTo(currentApplicationId))(any[HeaderCarrier])
      verify(mockUserService).save(any[CachedData])(any[HeaderCarrier])
    }
    "redirect to home page" in new TestFixture {
      val applicationRouteConfig =  new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val result = controller(currentCandidateWithEdipApp, applicationRouteConfig).submit()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockSecurityEnvironment = mock[security.SecurityEnvironment]
    val mockUserService = mock[UserService]

    class TestableSubmitApplicationController extends SubmitApplicationController(mockApplicationClient, mockCacheClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = mockSecurityEnvironment
      val appRouteConfigMap = Map.empty[ApplicationRoute, ApplicationRouteState]
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller(implicit candidateWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteConfig: ApplicationRouteState = defaultApplicationRouteState) = new TestableSubmitApplicationController{
      override val CandidateWithApp: CachedDataWithApp = candidateWithApp
      override implicit val appRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState] =
        Map(Faststream -> appRouteConfig, Edip -> appRouteConfig, Sdip -> appRouteConfig)
    }

    def defaultApplicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = false
      val applicationsStartDate = None
    }
  }
}
