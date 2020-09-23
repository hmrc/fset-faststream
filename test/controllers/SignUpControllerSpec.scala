/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import forms.{SignUpForm, SignupFormGenerator}
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models.{CachedDataExample, CachedDataWithApp}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import testkit.MockitoImplicits._
import testkit.TestableSecureActions

class SignUpControllerSpec extends BaseControllerSpec {

  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))

  val applicationsClosedPanelId = "id=\"applicationsClosed\""
  val faststreamClosed = "Unfortunately, applications for the Civil Service Fast Stream are now closed."
  val faststreamEligible = "Are you eligible to apply for the Civil Service Fast Stream?"
  val edipClosed = "Unfortunately, applications for the Early Diversity Internship Programme are now closed."
  val edipEligible = "Are you eligible to apply for the Early Diversity Internship Programme (EDIP)?"
  val sdipClosed = "Unfortunately, applications for the Summer Diversity Internship Programme are now closed."
  val sdipEligible = "Are you eligible to apply for the Summer Diversity Internship Programme (SDIP)?"

  "present" should {
    "display the sign up page and allow new accounts to be created" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content mustNot include(faststreamClosed)
      content mustNot include(edipClosed)
      content mustNot include(sdipClosed)
    }

    "display the sign up page but not allow new sdip accounts to be created when sdip is closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> defaultAppRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content mustNot include(sdipEligible)
      content must include(faststreamEligible)
      content must include(sdipClosed)
      content must include(edipEligible)
    }

    "display the sign up page but not allow new edip accounts to be created when edip is closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> appRouteState, Sdip -> defaultAppRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(sdipEligible)
      content must include(faststreamEligible)
      content must include(edipClosed)
      content mustNot include(edipEligible)
    }

    "prevent any new accounts being created when all application routes are closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(applicationsClosedPanelId)
      content mustNot include("Create account")
    }

    "prevent any new accounts being created when all application routes are closed and an invalid signup code is supplied" in new TestFixture {
      val invalidSignupCode = "abcd"
      when(mockApplicationClient.afterDeadlineSignupCodeUnusedAndValid(any())(any()))
        .thenReturnAsync(AfterDeadlineSignupCodeUnused(unused = false))

      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present(Some(invalidSignupCode))(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(applicationsClosedPanelId)
      content mustNot include("Create account")
    }

    "show the signup page when all application routes are closed but the user has a valid signup code" in new TestFixture {
      val validSignupCode = "abcd"
      when(mockApplicationClient.afterDeadlineSignupCodeUnusedAndValid(any())(any())).thenReturnAsync(
        AfterDeadlineSignupCodeUnused(unused = true, expires = Some(DateTime.now.plusDays(2)))
      )

      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }

      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present(Some(validSignupCode))(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(sdipEligible)
      content must include(faststreamEligible)
      content must include(edipEligible)
    }
  }

  "sign up" should {
    "display fast stream applications closed message" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val (_, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(appRouteConfigMap).signUp(None)(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present(None).url))
      flash(result).data mustBe Map("warning" -> "applicationRoute.Faststream.closed")
    }

    "display fast stream applications not started message" in new TestFixture {
      val appRouteState =  new ApplicationRouteState {
        val newAccountsStarted = false
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = Some(LocalDateTime.parse("2016-12-06T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val (data, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(appRouteConfigMap).signUp(None)(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present(None).url))
      val msg = "applicationRoute.Faststream.notOpen"
      flash(result).data mustBe Map("warning" -> msg)
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val defaultAppRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None
    }

    val defaultAppRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)

    def controller(newAppRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState]) = {
      val formWrapper = new SignUpForm
      new SignUpController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper, mockSignInService,
      mockApplicationClient, mockUserManagementClient, formWrapper) with TestableSecureActions {
        override val appRouteConfigMap = newAppRouteConfigMap
      }
    }
  }
}
