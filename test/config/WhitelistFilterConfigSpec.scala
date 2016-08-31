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

package config

import org.scalatestplus.play.{ OneAppPerTest, PlaySpec }
import java.util.Base64

import play.api.test._
import org.scalatest.TestData
import play.api.test.Helpers._

class WhitelistFilterConfigSpec extends PlaySpec with OneAppPerTest {

  implicit override def newAppForTest(td: TestData): FakeApplication =
    FakeApplication(additionalConfiguration = Map(
        "whitelistExcludedCalls" -> Base64.getEncoder.encodeToString("/ping/ping,/healthcheck".getBytes),
        "whitelist" -> Base64.getEncoder.encodeToString("11.22.33.44".getBytes)
      ),
     withGlobal = Some(ProductionFrontendGlobal))

  "FrontendAppConfig" must {
    "return a valid config item" when {
      "the whitelist exclusion paths are requested" in {
        FrontendAppConfig.whitelistExcluded mustBe Seq("/ping/ping", "/healthcheck")
      }
      "the whitelist IPs are requested" in {
        FrontendAppConfig.whitelist mustBe Seq("11.22.33.44")
      }
    }
  }

  "ProductionFrontendGlobal" must {
    "let requests passing" when {
      "coming from an IP in the white list" in {
        val request = FakeRequest(GET, "/fset-fast-stream/signup").withHeaders("True-Client-IP" -> "11.22.33.44")
        val Some(result) = route(app, request)

        status(result) mustBe (OK)
      }

      "coming from an IP NOT in the white list, not in an excluded path" in {
        val request = FakeRequest(GET, "/fset-fast-stream/signup").withHeaders("True-Client-IP" -> "93.00.33.33")
        val Some(result) = route(app, request)

        // Because of the redirect to the landing page
        status(result) mustBe (SEE_OTHER)
        redirectLocation(result) mustBe Some("https://www.tax.service.gov.uk/outage-fset-faststream/index.html")
      }

      "coming from an IP NOT in the white list, but with white-listed path" in {
        val request = FakeRequest(GET, "/ping/ping").withHeaders("True-Client-IP" -> "93.00.33.33")
        val Some(result) = route(app, request)

        status(result) mustBe (OK)
      }
    }
  }
}
