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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config.CSRHttp
import connectors.UserManagementClient.EmailTakenException
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.play.http.HeaderCarrier

class UserManagementClientSpec extends ConnectorSpec with OneServerPerSuite {
  implicit val hc = HeaderCarrier()

  private val connector = new UserManagementClient {
    val http: CSRHttp = CSRHttp
  }

  "sign up" should {
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
             |  "role":"candidate"
             |}
        """.stripMargin
        )
      ))

      val response = await(connector.register("test@email.com", "password", "peter", "griffin"))
      response.userId.toString should equal("4ca377d5-9b57-451b-9ca9-a8cd657c857f")
      response.email should equal("test@email.com")
      response.firstName should equal("peter")
      response.lastName should equal("griffin")
      response.role should equal("candidate")
      response.isActive should equal(true)
    }
  }

  "throw EmailTakenException if the email address is already taken" in {

    stubFor(post(urlPathEqualTo(s"/add")).willReturn(
      aResponse().withStatus(409)
    ))

    an[EmailTakenException] should be thrownBy await(connector.register("test@email.com", "pw", "fn", "ln"))
  }

}
