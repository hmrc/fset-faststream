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

import testkit.TestableSecureActions

class ApplicationControllerSpec extends BaseControllerSpec {

  "index" should {
    "redirect to sign in page" in new TestFixture {
      val result = controller.index()(fakeRequest)
      assertPageRedirection(result, routes.SignInController.signIn().url)
    }
  }

  "terms" should {
    "load terms page" in new TestFixture {
      val result = controller.terms()(fakeRequest)
      assertPageTitle(result, "Terms and conditions")
    }
  }

  "privacy" should {
    "load privacy page" in new TestFixture {
      val result = controller.privacy()(fakeRequest)
      assertPageTitle(result, "Privacy and cookies")
    }
  }

  "helpdesk" should {
    "load helpdesk page" in new TestFixture {
      val result = controller.helpdesk()(fakeRequest)
      assertPageTitle(result, "Contact us")
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val controller = new ApplicationController(
      mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper) with TestableSecureActions
  }
}
