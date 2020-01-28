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

import config.SecurityEnvironmentImpl
import connectors.{ ApplicationClient, UserManagementClient }
import connectors.UserManagementClient.{ TokenEmailPairInvalidException, TokenExpiredException }
import models.CachedData
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.{ Request, Result, Results }
import play.api.test.Helpers._
import security.{ SignInService, SilhouetteComponent }
import testkit.{ BaseControllerSpec, TestableSecureActions }

import scala.concurrent.Future

class ActivationControllerSpec extends BaseControllerSpec {
  val mockApplicationClient = mock[ApplicationClient]
  val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]
  val mockUserManagementClient = mock[UserManagementClient]
  val mockSignInService = mock[SignInService]

  import models.SecurityUserExamples._

  class TestableActivationController extends ActivationController(mockApplicationClient,
    mockUserManagementClient) with TestableSignInService
    with TestableSecureActions {
    val signInService = mockSignInService
    override val env = mockSecurityEnvironment
    override lazy val silhouette = SilhouetteComponent.silhouette
  }

  def controller = new TestableActivationController

  "Activation Controller present" should {
    "redirect to home page for active user" in {
      val result = controller.present()(fakeRequest)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
      flash(result).data must be (Map("warning" -> "You've already activated your account"))
    }

    "redirect to registration page for inactive user" in {
      val controllerForInactiveUser = new TestableActivationController {
        override val candidate: CachedData = InactiveCandidate
      }

      val result = controllerForInactiveUser.present()(fakeRequest)

      status(result) must be(OK)
      contentAsString(result) must include("<title>Activate your account")
    }
  }

  "Activation Controller submit" should {
    "activate user when activation form is valid" in {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(currentEmail), eqTo(ValidToken))(any())).thenReturn(Future.successful(()))
      when(mockSignInService.signInUser(
        eqTo(currentCandidate.user.copy(isActive = true)),
        eqTo(mockSecurityEnvironment),
        any[Result])(any[Request[_]])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))

      val result = controller.submit()(Request)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.HomeController.present().url))
    }

    "reject form when validation failed" in {
      val TooShortToken = "A"
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> TooShortToken)

      val result = controller.submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("The activation code must have 7 characters")
    }

    "reject form when token expired" in {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(currentEmail), eqTo(ValidToken))(any()))
        .thenReturn(Future.failed(new TokenExpiredException))

      val result = controller.submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("This activation code has expired")
    }

    "reject form when token and email pair invalid" in {
      val Request = fakeRequest.withFormUrlEncodedBody("activation" -> ValidToken)
      when(mockUserManagementClient.activate(eqTo(ActiveCandidateUser.email), eqTo(ValidToken))(any()))
        .thenReturn(Future.failed(new TokenEmailPairInvalidException))

      val result = controller.submit()(Request)

      status(result) must be(OK)
      contentAsString(result) must include("Enter a correct activation code")
    }
  }

  "Activation Controller resend code" should {
    "resend the activation code" in {
      when(mockUserManagementClient.resendActivationCode(eqTo(ActiveCandidateUser.email))(any())).thenReturn(Future.successful(()))

      val result = controller.resendCode()(fakeRequest)

      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(routes.ActivationController.present().url))
      flash(result).data must be (Map("success" -> ("A new activation code has been sent. " +
        "Check your email.<p>If you can't see it in your inbox within a few minutes, " +
        "check your spam folder.</p>"))
      )
    }
  }
}
