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
import java.time.format.DateTimeFormatter

import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import forms.SignupFormGenerator
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models.{ CachedDataExample, CachedDataWithApp }
import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import testkit.BaseControllerSpec


class SignUpControllerSpec extends BaseControllerSpec {

  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))

  "present" should {
    "display the sign up page" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val result = controller(applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("Unfortunately, applications for the Civil Service Fast Stream are now closed.")
    }

    "display the sign up page with fast stream applications closed message" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val result = controller(applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content mustNot include("Unfortunately, applications for the Civil Service Fast Stream are now closed.")
    }
  }

  "sign up" should {
    "display fast stream applications closed message" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val (data, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(applicationRouteState).signUp()(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present().url))
      flash(result).data must be (Map("warning" -> "Sorry, applications for the Civil Service Fast Stream are now closed"))
    }
    "display fast stream applications not started message" in new TestFixture {
      val applicationRouteState =  new ApplicationRouteState {
        val newAccountsStarted = false
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = Some(LocalDateTime.parse("2016-12-06T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      }
      val (data, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(applicationRouteState).signUp()(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present().url))
      flash(result).data must be (Map("warning" -> "Sorry, applications for the Civil Service Fast Stream are opened from 06 Dec 2016"))
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockSecurityEnvironment = mock[security.SecurityEnvironment]
    val mockUserService = mock[UserService]

    class TestableSignUpController(val applicationRouteState: ApplicationRouteState)
      extends SignUpController(mockApplicationClient, mockCacheClient) with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = mockSecurityEnvironment
      val appRouteConfigMap = Map(Faststream -> applicationRouteState, Edip -> applicationRouteState, Sdip -> applicationRouteState)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller(applicationRouteState: ApplicationRouteState) = new TestableSignUpController(applicationRouteState)
  }
}
