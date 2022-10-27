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

import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import forms.SignInForm
import models.CachedDataExample
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.Helpers._
import security._
import testkit.{NoIdentityTestableCSRUserAwareAction, TestableCSRUserAwareAction}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.Right

class SignInControllerSpec extends BaseControllerSpec {

  "present" should {
    "Return sign in page if no user has signed in" in new TestFixture {
      val result = signInController().present(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include ("Sign in | Apply for the Civil Service Fast Stream")
    }

    "Return home if a user has signed in" in new TestFixture {
      val result = signInControllerAfterSignIn().present(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.HomeController.present().toString())
    }
  }

  "sign in" should {
    "return to home page if email and password are submitted empty" in new TestFixture {
      val request = fakeRequest.withMethod("POST").withFormUrlEncodedBody(
        "signIn" -> "",
        "signInPassword" -> ""
      )
      val result = signInController().signIn(request)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("<title>Error: Sign in | Apply for the Civil Service Fast Stream")
      content must include ("error.required.signIn")
      content must include ("error.required.password")
    }

    "return to home page if password is not passed" in new TestFixture {
      val request = fakeRequest.withMethod("POST").withFormUrlEncodedBody(
        "signIn" -> "xxx",
        "signInPassword" -> ""
      )
      val result = signInController().signIn(request)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("<title>Error: Sign in | Apply for the Civil Service Fast Stream")
      content must include ("error.required.password")
    }

    "return to home page if the user has been locked" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.LockedCandidateUser),
        any[Result])(any[Request[_]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.LockedCandidateUser)))

      val result = signInController().signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.LockAccountController.present.toString())
      session(result).get("email") mustBe Some(CachedDataExample.LockedCandidateUser.email)
    }

    "sign in user if he/ she is active" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.ActiveCandidateUser),
        any[Result])(any[Request[_]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.ActiveCandidateUser)))

      val result = signInController(mockSignInService).signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.HomeController.present().toString())
    }

    "sign in user if he/ she and redirect to activation page" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.NonActiveCandidateUser), any[Result])
        (any[Request[_]], any[HeaderCarrier])
        ).thenReturn(Future.successful(Results.Redirect(routes.ActivationController.present)))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.NonActiveCandidateUser)))

      val result = signInController(mockSignInService).signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.ActivationController.present.toString())
    }

    "show invalid role message if user has an invalid role" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(InvalidRole)))

      val result = signInController(signInService).signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("error.invalidRole")
    }

    "handle cross site scripting xss attempt in the flash cookie" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(InvalidRole)))

      val signInRequestWithXssFlashCookie = signInRequest.withFlash(
        "danger" -> "Some text<script>alert('XSS within PLAY_FLASH cookie')</script>)"
      )
      val result = signInController(signInService).signIn(signInRequestWithXssFlashCookie)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("error.invalidRole")
      val apostropheEncoded = "&#x27;"
      val lessThanEncoded = "&lt;"
      val greaterThanEncoded = "&gt;"
      val expectedEscapedText = s"Some text${lessThanEncoded}script${greaterThanEncoded}" +
        s"alert(${apostropheEncoded}XSS within PLAY_FLASH cookie${apostropheEncoded})${lessThanEncoded}/script${greaterThanEncoded})"
      content must include (expectedEscapedText)
    }

    "show invalid credentials message if invalid credentials are passed" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(InvalidCredentials)))

      val result = signInController(signInService).signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("signIn.invalid")
    }

    "show last attemp message if user has tried to sign in too many times" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(LastAttempt)))

      val result = signInController(signInService).signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("last.attempt")
    }

    "show account locked message if user has just been locked while trying to sign in" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(AccountLocked)))

      val result = signInController().signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.LockAccountController.present.toString())
      session(result).get("email") mustBe Some("xxx")
    }

    "sign out" should {
      "sign out if you are not signed in" in new TestFixture {
        when(mockAuthenticatorService.retrieve(any())).thenReturn(Future.successful(None))

        val result = signInController(signInService).signOut(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustEqual Some(routes.SignInController.present.toString())
        flash(result) mustBe Flash(Map("danger" -> "You have already signed out"))
      }

      "sign out if you are signed in" in new TestFixture {
        when(mockAuthenticatorService.discard(any[SessionAuthenticator], any[Result])(any[RequestHeader])).thenReturn(
          Future.successful(AuthenticatorResult.apply(Results.Redirect(routes.SignInController.present)))
        )

        val result = signInControllerAfterSignIn().signOut(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustEqual Some(routes.SignInController.present.toString())
        //flash(result) mustBe Flash(Map("success" -> "feedback"))
      }
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val signInRequest = fakeRequest.withMethod("POST").withFormUrlEncodedBody(
      "signIn" -> "xxx",
      "signInPassword" -> "yyyy"
    )

    val mockAuthenticator = mock[SessionAuthenticator]

    val formWrapper = new SignInForm
    // In same tests mockSignInService is not appropriate and it is easier to use the real implementation with mocked dependencies.
    val signInService = new SignInService(mockConfig, mockSecurityEnv, mockApplicationClient, mockNotificationTypeHelper, formWrapper)

    class TestableSignInController extends SignInController(
      mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper,
      mockSignInService, formWrapper)

    // In same tests mockSignInService is not appropriate and it is easier to use the real implementation with mocked dependencies.
    // With this method, you can pass in newSignInService the preferred sign in service implementation.
    def signInController(newSignInService: SignInService = mockSignInService) = {
      new TestableSignInController with NoIdentityTestableCSRUserAwareAction {
        override val signInService = newSignInService
      }
    }
    def signInControllerAfterSignIn(newSignInService: SignInService = signInService) = {
      new TestableSignInController with TestableCSRUserAwareAction {
        override val signInService = newSignInService
      }
    }

    when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.ActiveCandidateUser)))
    when(mockAuthenticatorService.retrieve(any())).thenReturn(Future.successful(Some(mockAuthenticator)))
  }
}
