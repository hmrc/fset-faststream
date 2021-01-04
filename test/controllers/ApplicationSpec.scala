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

import com.kenshoo.play.metrics.PlayModule
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testkit.{BaseSpec, SilhouetteFakeModule}

class ApplicationSpec extends BaseSpec with GuiceOneAppPerSuite {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(new SilhouetteFakeModule())
    .disable[PlayModule]
    .build

  "Application" should {
    "send 404 on a bad request" in {
      val Some(result) = route(app, FakeRequest(GET, "/boo"))
      status(result) mustBe NOT_FOUND
    }
  }
}
