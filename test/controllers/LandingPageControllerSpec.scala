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

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._

class LandingPageControllerSpec extends PlaySpec {

  "Landing page controller" should {

    "redirect to sign-in" in {
      val request = FakeRequest(GET, controllers.routes.Application.index().url)

      val result = call(LandingPageController.index, request)

      status(result) must be(303)
      redirectLocation(result).get must be(controllers.routes.SignInController.signIn().url)
    }

  }
}
