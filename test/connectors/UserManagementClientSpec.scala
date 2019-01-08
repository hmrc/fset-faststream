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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config.CSRHttp
import connectors.UserManagementClient.EmailTakenException
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

class UserManagementClientSpec extends UnitWithAppSpec with ConnectorSpec {
  implicit val hc = HeaderCarrier()

  private val connector = new UserManagementClient {
    val http: CSRHttp = CSRHttp
  }

  // FIXME Test ignored because of failure in build server that is not reproducible locally
  "sign up" ignore {
    "return user upon success" in {

      stubFor(post(urlPathEqualTo(s"/add")).willReturn(
        aResponse().withStatus(201).withBody(
          s"""
             |{
             |  "userId":"4ca377d5-9b57-451b-9ca9-a8cd657c857f",
             |  "email":"test@email.com",
             |  "firstName":"peter",
             |  "lastName":"griffin",
             |  "isActive":true,
             |  "lockStatus":"UNLOCKED",
             |  "role":"candidate",
             |  "service":"faststream",
             |  "disabled":false
             |}
        """.stripMargin
        )
      ))

      val response = connector.register("test@email.com", "password", "peter", "griffin").futureValue
      response.userId.toString mustBe "4ca377d5-9b57-451b-9ca9-a8cd657c857f"
      response.email mustBe "test@email.com"
      response.firstName mustBe "peter"
      response.lastName mustBe "griffin"
      response.roles mustBe List("candidate")
      response.isActive mustBe true
    }

    "throw EmailTakenException if the email address is already taken" in {

      stubFor(post(urlPathEqualTo(s"/add")).willReturn(
        aResponse().withStatus(409)
      ))

      connector.register("test@email.com", "pw", "fn", "ln").failed.futureValue mustBe an[EmailTakenException]
    }
  }

}
