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
import play.api.mvc.{ Action, Result }
import play.api.mvc.Results._

import play.api.http.Status._



class WhitelistFilterConfigSpec extends PlaySpec with OneAppPerTest {

  implicit override def newAppForTest(td: TestData): FakeApplication =
    FakeApplication(additionalConfiguration = Map(
        "whitelistExcludedCalls" -> Base64.getEncoder.encodeToString("/ping/pong,/healthcheck".getBytes),
        "whitelist" -> Base64.getEncoder.encodeToString("11.22.33.44".getBytes)
      )

    )

  "FrontendAppConfig" must {
    "return a valid config item" when {
      "the whitelist exclusion paths are requested" in {

          FrontendAppConfig.whitelistExcluded mustBe Seq("/ping/pong", "/healthcheck")
      }
      "the whitelist IPs are requested" in {
          FrontendAppConfig.whitelist mustBe Seq("11.22.33.44")
      }

    }

    "WhitelistFilter" must {
      "let requests passing" when {
        "coming from an IP in the white list" in {
          val underTest = WhitelistFilter
          // Play.current is set at this point...
          val rh = FakeRequest(GET, "/signup").withHeaders("True-Client-IP" -> "11.22.33.44")
          val action = Action(Ok("success"))
          val result = underTest(action)(rh).run

          status(result) mustBe (OK)

          // Caused by: java.lang.Exception: metrics plugin is not configured
            /*running(TestServer(33337)) {

              val response = await(WS.url("http://localhost:33337/signup").withHeaders("True-Client-IP" -> "11.22.33.44").get())
              response.status mustBe(OK)
            }*/
        }

        "coming from an IP NOT in the white list" in {
          val underTest = WhitelistFilter
          // Play.current is set at this point...
          val rh = FakeRequest(GET, "/signup").withHeaders("True-Client-IP" -> "93.00.33.33")
          val action = Action(Ok("success"))
          val result = underTest(action)(rh).run

          // Because of the redirect to the landing page
          status(result) mustBe (SEE_OTHER)
        }
      }
    }
  }
}
