/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import config.{AuthConfig, FrontendAppConfig, UserManagementConfig, UserManagementUrl}
import connectors.UserManagementClient.EmailTakenException
import connectors.exchange.{AddUserRequest, UserResponse}
import models.UniqueIdentifier
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class UserManagementClientSpec extends BaseConnectorSpec {

  val userId1 = UniqueIdentifier(UUID.randomUUID.toString)

  "sign up" should {
    val endpoint = "localhost/faststream/add"
    val addRequest = AddUserRequest("test@email.com", "password", "peter", "griffin", List("candidate"),
      "faststream")

    "return user upon success" in new TestFixture {
      val userResponse = UserResponse("peter", "griffin", None, true, userId1, "test@email.com",
        false, "UNLOCKED", List("candidate"), "faststream", None)

      when(mockHttp.POST[AddUserRequest, UserResponse](eqTo(endpoint), eqTo(addRequest), any[Seq[(String, String)]])(
        any[Writes[AddUserRequest]],
        any[HttpReads[UserResponse]],
        any[HeaderCarrier],
        any[ExecutionContext]))
        .thenReturn(Future.successful(userResponse))

      val response = client.register("test@email.com", "password", "peter", "griffin").futureValue

      response mustBe userResponse
    }

    "throw EmailTakenException if the email address is already taken" in new TestFixture {
      when(mockHttp.POST[AddUserRequest, UserResponse](eqTo(endpoint), eqTo(addRequest), any[Seq[(String, String)]])(
        any[Writes[AddUserRequest]],
        any[HttpReads[UserResponse]],
        any[HeaderCarrier],
        any[ExecutionContext]))
        .thenReturn(Future.failed(UpstreamErrorResponse("", 409, 0, Map.empty)))

      val response = client.register("test@email.com", "password", "peter", "griffin").failed.futureValue
      response mustBe an[EmailTakenException]
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val mockConfig = new FrontendAppConfig(mockConfiguration, mockEnvironment) {
      val userManagementUrl = UserManagementUrl("localhost")
      override lazy val userManagementConfig = UserManagementConfig(userManagementUrl)
      override lazy val authConfig = AuthConfig("faststream")
    }
    val client = new UserManagementClient(mockConfig, mockHttp)
  }
}
