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

import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import testkit.BaseControllerSpec

class ApplicationControllerSpec extends BaseControllerSpec {
  val mockApplicationClient = mock[ApplicationClient]
  val mockCacheClient = mock[CSRCache]

  class TestableApplicationController extends ApplicationController(mockApplicationClient, mockCacheClient) with TestableSecureActions {
    val http: CSRHttp = CSRHttp
    override protected def env = securityEnvironment
  }

  def controller = new TestableApplicationController

  "index" should {
    "redirect to sign in page" in {
      val result = controller.index()(fakeRequest)
      assertPageRedirection(result, routes.SignInController.signIn().url)
    }
  }

  "terms" should {
    "load terms page" in {
      val result = controller.terms()(fakeRequest)
      assertPageTitle(result, "Terms and conditions")
    }
  }

  "privacy" should {
    "load privacy page" in {
      val result = controller.privacy()(fakeRequest)
      assertPageTitle(result, "Privacy and cookies")
    }
  }

  "helpdesk" should {
    "load helpdesk page" in {
      val result = controller.helpdesk()(fakeRequest)
      assertPageTitle(result, "Contact us")
    }
  }
}
