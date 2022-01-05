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

package config

import java.util.Base64

import org.scalatest.TestData
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, Environment, Mode}

class FaststreamWhitelistFilterConfigSpec extends PlaySpec with GuiceOneAppPerTest {

  val dummyIP1 = "11.22.33.44"
  val dummyIP2 = "8.8.8.8"
  val dummyIP3 = "93.00.33.33"

    override def newAppForTest(td: TestData): Application = new GuiceApplicationBuilder()
      .in(Environment.simple(mode = Mode.Prod, path = new java.io.File(".")))
      .configure(Map(
        "whitelistExcludedCalls" -> Base64.getEncoder.encodeToString("/ping/ping,/healthcheck".getBytes),
        "whitelist" -> Base64.getEncoder.encodeToString(s"$dummyIP1,$dummyIP2".getBytes),
        "whitelistFileUpload" -> Base64.getEncoder.encodeToString(s"$dummyIP2".getBytes),
        "play.http.filters" -> "config.ProductionFaststreamFilters"
      ))
      .build

  "FrontendAppConfig" must {
    "return a valid config item" when {
      "the whitelist exclusion paths are requested" in {
        val environment = Environment.simple(mode = Mode.Prod)
        val frontendAppConfig = new FrontendAppConfig(app.configuration, environment)
        frontendAppConfig.whitelistExcluded mustBe Seq("/ping/ping", "/healthcheck")
      }
      "the whitelist IPs are requested" in {
        val environment = Environment.simple(mode = Mode.Prod)
        val frontendAppConfig = new FrontendAppConfig(app.configuration, environment)
        frontendAppConfig.whitelist mustBe Seq(dummyIP1, dummyIP2)
      }
      "the file upload whitelist IPs are requested" in {
        val environment = Environment.simple(mode = Mode.Prod)
        val frontendAppConfig = new FrontendAppConfig(app.configuration, environment)
        frontendAppConfig.whitelistFileUpload mustBe Seq(dummyIP2)
      }
    }
  }

  "WhiteListFilter" must {
    "let requests pass" when {
      "coming from an IP in the white list must work as normal" in {
        val request = FakeRequest(GET, "/fset-fast-stream/signup").withHeaders("True-Client-IP" -> dummyIP1)
        val Some(result) = route(app, request)

        status(result) mustBe OK
      }

      "coming from an IP NOT in the white-list and not with a white-listed path must be redirected" in {
        val request = FakeRequest(GET, "/fset-fast-stream/signup").withHeaders("True-Client-IP" -> dummyIP3)
        val Some(result) = route(app, request)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("https://www.apply-civil-service-fast-stream.service.gov.uk/shutter/fset-faststream/index.html")
      }

      "coming from an IP NOT in the white-list, but with a white-listed path must work as normal" in {
        val request = FakeRequest(GET, "/ping/ping").withHeaders("True-Client-IP" -> dummyIP3)
        val Some(result) = route(app, request)

        status(result) mustBe OK
      }

      "coming without an IP header must fail" in {
        val request = FakeRequest(GET, "/fset-fast-stream/signup")
        val Some(result) = route(app, request)

        status(result) mustBe NOT_IMPLEMENTED
      }

      "Uploading a file from an IP not on the main whitelist" in {
        val request = FakeRequest(GET, "/fset-fast-stream/file-submission/foobar").withHeaders("True-Client-IP" -> dummyIP3)
        val Some(result) = route(app, request)

        status(result) mustBe FORBIDDEN
      }

      "Uploading a file from an IP on the main whitelist, but not on the file upload whitelist" in {
        val request = FakeRequest(GET, "/fset-fast-stream/file-submission/foobar").withHeaders("True-Client-IP" -> dummyIP1)
        val Some(result) = route(app, request)

        status(result) mustBe FORBIDDEN
      }

      "Uploading a file from an IP on the main whitelist, and on the file upload whitelist" in {
        val request = FakeRequest(GET, "/fset-fast-stream/file-submission/foobar").withHeaders("True-Client-IP" -> dummyIP2)
        val Some(result) = route(app, request)

        status(result) mustBe NOT_FOUND
      }
    }
  }
}
