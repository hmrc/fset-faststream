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

import connectors.UserManagementClient.{TokenEmailPairInvalidException, TokenExpiredException}
import forms.ActivateAccountForm
import models.CachedData
import models.SecurityUserExamples._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.{Request, Result, Results}
import play.api.test.Helpers._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ActivationControllerSpec extends BaseControllerSpec {

  "Activation Controller present" should {
    "redirect to home page for active user" in new TestFixture {
      val result = controller(ActiveCandidate).present()(fakeRequest)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      flash(result).data must be (Map("warning" -> "activation.already"))
    }

    "redirect to registration page for inactive user" in new TestFixture {
      val result = controller(InactiveCandidate).present()(fakeRequest)

      status(result) must be(OK)
      contentAsString(result) must include("<title>Activate your account")
    }
  }

  "Activation Controller submit" should {
    "activate user when activation form is valid" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(currentEmail), eqTo(ValidToken))(any())).thenReturn(Future.successful(()))
      when(mockSignInService.signInUser(
        eqTo(currentCandidate.user.copy(isActive = true)),
        any[Result])(any[Request[_]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))

      val result = controller(ActiveCandidate).submit()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }

    "reject form when validation failed" in new TestFixture {
      val TooShortToken = "A"
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> TooShortToken)

      val result = controller(ActiveCandidate).submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("activation.wrong-format")
    }

    "reject form when token expired" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(currentEmail), eqTo(ValidToken))(any()))
        .thenReturn(Future.failed(new TokenExpiredException))

      val result = controller(ActiveCandidate).submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("expired.activation-code")
    }

    "reject form when token and email pair invalid" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(ActiveCandidateUser.email), eqTo(ValidToken))(any()))
        .thenReturn(Future.failed(new TokenEmailPairInvalidException))

      val result = controller(ActiveCandidate).submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("wrong.activation-code")
    }
  }

  "Activation Controller resend code" should {
    "resend the activation code" in new TestFixture {
      when(mockUserManagementClient.resendActivationCode(eqTo(ActiveCandidateUser.email))(any())).thenReturn(Future.successful(()))

      val result = controller(ActiveCandidate).resendCode()(fakeRequest)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.ActivationController.present().url))
      flash(result).data must be (Map("success" -> ("activation.code-resent"))
      )
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val formWrapper = new ActivateAccountForm
    def controller(givenCandidate: CachedData) = {
      new ActivationController(mockConfig,
        stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockUserManagementClient,
        mockNotificationTypeHelper, mockSignInService, formWrapper) with TestableSecureActions {
        override val candidate: CachedData = givenCandidate
      }
    }
  }
}
