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

import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.impl.authenticators.{SessionAuthenticator, SessionAuthenticatorService}
import connectors.ApplicationClient
import models.CachedDataExample
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc.{Flash, Request, Result, Results}
import play.api.test.Helpers._
import security._
import testables.{NoIdentityTestableCSRUserAwareAction, TestableCSRUserAwareAction}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.Right

class LockAccountControllerSpec extends BaseControllerSpec {

  "present" should {
    "Return locked page with no email if there is no email in flash context" in new TestFixture {
      val result = lockAccountController.present(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("Account locked</h1>")
    }

    "Return locked page with email if there is email in flash context" in new TestFixture {
      val result = lockAccountController.present(fakeRequest.withFlash("email" -> "testEmailXYZ@mailinator.com"))

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("Account locked</h1>")
      content must include("testEmailXYZ@mailinator.com")
    }
  }


  "submit" should {
    "flash a error message to show email is missing when email is not passed" in new TestFixture {
      val result = lockAccountController.submit(fakeRequest.withFormUrlEncodedBody(
        "email" -> ""
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.LockAccountController.present().toString())
      flash(result) mustBe Flash(Map("email" -> ""))
    }

    "present reset password page when email is passed" in new TestFixture {
      val lockAccountRequest = fakeRequest.withFormUrlEncodedBody(
        "email" -> "testEmail123@mailinator.com"
      )
      val result = lockAccountController.submit(lockAccountRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustEqual Some(routes.PasswordResetController.presentReset(Some("testEmail123@mailinator.com")).toString())
      flash(result) mustBe Flash(Map("email" -> "testEmail123@mailinator.com"))
    }
  }


  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockApplicationClient = mock[ApplicationClient]
    val mockEnvironment = mock[SecurityEnvironment]

    class TestableLockAccountController extends LockAccountController(mockApplicationClient) {
      override protected def env = mockEnvironment
    }

    def lockAccountController = new LockAccountController(mockApplicationClient) with NoIdentityTestableCSRUserAwareAction
  }
}
