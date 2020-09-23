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

import play.api.test.Helpers._
import testkit.NoIdentityTestableCSRUserAwareAction
import uk.gov.hmrc.http.SessionKeys

class LockAccountControllerSpec extends BaseControllerSpec {

  "present" should {
    "Return locked page with no email if there is no email in session" in new TestFixture {
      val result = lockAccountController.present(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("Account locked</h1>")
    }

    "Return locked page with email if there is email in session" in new TestFixture {
      val result = lockAccountController.present(fakeRequest.
        withSession("email" -> "testEmailXYZ@mailinator.com"))

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
      redirectLocation(result) mustBe Some(routes.LockAccountController.present().toString)
      session(result).get("email") mustBe None
    }

    "present reset password page when email is passed" in new TestFixture {
      val lockAccountRequest = fakeRequest.withFormUrlEncodedBody(
        "email" -> "testEmail123@mailinator.com"
      ).withSession(SessionKeys.sessionId -> "session-0379e8ad-3797-4c7f-b80f-2279b5f0819a")

      val result = lockAccountController.submit(lockAccountRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.PasswordResetController.presentReset().toString)
      val sess = session(result)
      sess.get("email") mustBe Some("testEmail123@mailinator.com")
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    def lockAccountController = {
      new LockAccountController(mockConfig, stubMcc, mockSecurityEnv,
        mockSilhouetteComponent, mockNotificationTypeHelper) with NoIdentityTestableCSRUserAwareAction
    }
  }
}
