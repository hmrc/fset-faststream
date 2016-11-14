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

import config.CSRCache
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.InvigilatedTestUrl
import connectors.{ ApplicationClient, UserManagementClient }
import play.api.http.Status.{ OK => _, SEE_OTHER => _, _ }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testkit.BaseControllerSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class InvigilatedControllerSpec extends BaseControllerSpec {

  "present" should {
    "Return a start invigilated e-tray page" in new TestFixture {
      val result = underTest.present(validateTokenRequest)
      status(result) mustBe OK
      contentAsString(result) must include ("Start invigilated e-tray")
    }
  }

  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val email = "marcel.cerdan@bogus.ww.z"
    val token = "HNJ8UTF"

    val fake = FakeRequest(POST, controllers.routes.InvigilatedController.verifyToken().url)

    val validateTokenRequest = fake.withFormUrlEncodedBody(
      "email" -> email,
      "token" -> token
    )

    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockUserManagementClient = mock[UserManagementClient]
    val testUrl = "https://localhost/xxx/yyy"
    val successfulValidationResponse = Future.successful(InvigilatedTestUrl(testUrl))
    val failedValidationResponse = Future.failed(new TokenEmailPairInvalidException())

    class TestableInvigilatedController extends InvigilatedController(mockApplicationClient, mockCacheClient, mockUserManagementClient)

    def underTest = new TestableInvigilatedController

  }
}
