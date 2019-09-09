/*
 * Copyright 2019 HM Revenue & Customs
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

import config.{ CSRHttp, SecurityEnvironmentImpl }
import connectors.ApplicationClient
import connectors.ApplicationClient.TestForTokenExpiredException
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.InvigilatedTestUrl
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.SilhouetteComponent
import testkit.BaseControllerSpec

import scala.concurrent.Future

class InvigilatedControllerSpec extends BaseControllerSpec {

  "present" should {
    "display the Start invigilated phase 2 tests page" in new TestFixture {
      val result = underTest.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Start invigilated phase 2 tests")
    }
  }

  "verifyToken" should {
    "redirect to test url upon successful token validation" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("email" -> "test@test.com", "token" -> "KI6U8T")
      when(mockApplicationClient.verifyInvigilatedToken(eqTo("test@test.com"), eqTo("KI6U8T"))(any())).thenReturn(successfulValidationResponse)

      val result = underTest.verifyToken()(Request)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(testUrl))
    }

    "display the Start invigilated phase 2 tests page with an error message when the validation is not successful" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("email" -> "test@test.com", "token" -> "KI6U8T")
      when(mockApplicationClient.verifyInvigilatedToken(eqTo("test@test.com"), eqTo("KI6U8T"))(any())).thenReturn(failedValidationResponse)

      val result = underTest.verifyToken()(Request)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Start invigilated phase 2 tests")
      content must include("Invalid email or access code")
    }

    "display the Start invigilated phase 2 tests page with an error message when the test is expired" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("email" -> "test@test.com", "token" -> "KI6U8T")
      when(mockApplicationClient.verifyInvigilatedToken(eqTo("test@test.com"), eqTo("KI6U8T"))(any())).thenReturn(testExpiredResponse)

      val result = underTest.verifyToken()(Request)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Start invigilated phase 2 tests")
      content must include("Test is expired")
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]

    val testUrl = "http://localhost:9284/fset-fast-stream/invigilated-phase2-tests"
    val successfulValidationResponse = Future.successful(InvigilatedTestUrl(testUrl))
    val failedValidationResponse = Future.failed(new TokenEmailPairInvalidException())
    val testExpiredResponse = Future.failed(new TestForTokenExpiredException())

    class TestableInvigilatedController extends InvigilatedController(mockApplicationClient) {
      val http: CSRHttp = CSRHttp
      override val env = mockSecurityEnvironment
      override lazy val silhouette = SilhouetteComponent.silhouette
    }

    val underTest = new TestableInvigilatedController
  }
}
