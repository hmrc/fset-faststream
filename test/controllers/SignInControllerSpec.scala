/*
 * Copyright 2017 HM Revenue & Customs
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

import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.impl.authenticators.{ SessionAuthenticator, SessionAuthenticatorService }
import config.CSRCache
import connectors.ApplicationClient
import models.CachedDataExample
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.{ Flash, Request, Result, Results }
import play.api.test.Helpers._
import security._
import testables.{ NoIdentityTestableCSRUserAwareAction, TestableCSRUserAwareAction }
import testkit.BaseControllerSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.Right

class SignInControllerSpec extends BaseControllerSpec {

  "present" should {
    "Return sign in page if no user has signed in" in new TestFixture {
      val result = signInController.present(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include ("Sign in | Apply for the Civil Service Fast Stream")
    }

    "Return home if a user has signed in" in new TestFixture {
      val result = signInControllerAfterSignIn.present(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.HomeController.present().toString())
    }
  }

  "sign in" should {
    "return to home page if email and password are submitted empty" in new TestFixture {
      val request = fakeRequest.withFormUrlEncodedBody(
        "signIn" -> "",
        "signInPassword" -> ""
      )
      val result = signInController.signIn(request)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("<title>Sign in | Apply for the Civil Service Fast Stream")
      content must include ("Enter your email")
      content must include ("Enter your password")
    }

    "return to home page if password is not passed" in new TestFixture {
      val request = fakeRequest.withFormUrlEncodedBody(
        "signIn" -> "xxx",
        "signInPassword" -> ""
      )
      val result = signInController.signIn(request)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("<title>Sign in | Apply for the Civil Service Fast Stream")
      content must include ("Enter your password")
    }

    "return to home page if the user has been locked" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.LockedCandidateUser),
        any[SecurityEnvironment],
        any[Result])(any[Request[_]])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.LockedCandidateUser)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.LockAccountController.present().toString())
      session(result).get("email") mustBe Some(CachedDataExample.LockedCandidateUser.email)
    }

    "sign in user if he/ she is active" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.ActiveCandidateUser),
        any[SecurityEnvironment],
        any[Result])(any[Request[_]])
      ).thenReturn(Future.successful(Results.Redirect(routes.HomeController.present())))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.ActiveCandidateUser)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.HomeController.present().toString())
    }

    "sign in user if he/ she and redirect to activation page" in new TestFixture {
      when(mockSignInService.signInUser(
        eqTo(CachedDataExample.NonActiveCandidateUser),
        any[SecurityEnvironment],
        any[Result])(any[Request[_]])
      ).thenReturn(Future.successful(Results.Redirect(routes.ActivationController.present())))
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.NonActiveCandidateUser)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.ActivationController.present().toString())
    }

    "show invalid role message if user has an invalid role" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(InvalidRole)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("You don't have access to this application.")
    }

    "show invalid credentials message if invalid credentials are passed" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(InvalidCredentials)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("Invalid email or password")
    }

    "show last attemp message if user has tried to sign in too many times" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(LastAttempt)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include ("Your account will be locked after another unsuccessful attempt to sign in")
    }

    "show account locked message if user has just been locked while trying to sign in" in new TestFixture {
      when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Left(AccountLocked)))

      val result = signInController.signIn(signInRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.LockAccountController.present().toString())
      session(result).get("email") mustBe Some("xxx")
    }

    "sign out" should {
      "sign out if you are not signed in" in new TestFixture {
        when(mockAuthenticatorService.retrieve(any())).thenReturn(Future.successful(None))

        val result = signInController.signOut(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustEqual Some(routes.SignInController.present().toString())
        flash(result) mustBe Flash(Map("danger" -> "You have already signed out"))
      }

      "sign out if you are signed in" in new TestFixture {
        when(mockAuthenticator.discard(any[Future[Result]])).thenReturn(Future.successful(Results.Redirect(routes.SignInController.present())))

        val result = signInControllerAfterSignIn.signOut(fakeRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustEqual Some(routes.SignInController.present().toString())
        //flash(result) mustBe Flash(Map("success" -> "feedback"))
      }
    }
  }

  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val signInRequest = fakeRequest.withFormUrlEncodedBody(
      "signIn" -> "xxx",
      "signInPassword" -> "yyyy"
    )

    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockSignInService = mock[SignInService]

    val mockEnvironment = mock[SecurityEnvironment]
    val mockCredentialsProvider = mock[CsrCredentialsProvider]
    val mockAuthenticatorService = mock[SessionAuthenticatorService]
    val mockEventBus = mock[EventBus]
    when(mockEnvironment.credentialsProvider).thenReturn(mockCredentialsProvider)
    when(mockEnvironment.authenticatorService).thenReturn(mockAuthenticatorService)
    when(mockEnvironment.eventBus).thenReturn(mockEventBus)

    val mockAuthenticator = mock[SessionAuthenticator]

    class TestableSignInController extends SignInController(mockApplicationClient, mockCacheClient) with TestableSignInService {
      override val signInService = mockSignInService
      override protected def env = mockEnvironment
    }

    def signInController = new TestableSignInController with NoIdentityTestableCSRUserAwareAction
    def signInControllerAfterSignIn = new TestableSignInController with TestableCSRUserAwareAction

    when(mockCredentialsProvider.authenticate(any())(any())).thenReturn(Future.successful(Right(CachedDataExample.ActiveCandidateUser)))
    when(mockAuthenticatorService.retrieve(any())).thenReturn(Future.successful(Some(mockAuthenticator)))
  }
}
