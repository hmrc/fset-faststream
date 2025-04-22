/*
 * Copyright 2023 HM Revenue & Customs
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
import config.{AuthConfig, MicroserviceAppConfig, UserManagementConfig}
import connectors.AuthProviderClient.{TokenEmailPairInvalidException, TokenExpiredException, TooManyResultsException}
import connectors.ExchangeObjects.{Candidate, UserAuthInfo}
import model.Exceptions.ConnectorException
import model.exchange.SimpleTokenResponse
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
// Added ShortTimeout as these tests sometimes fail on Jenkins with the default 150ms timeout defined in the default PatienceConfig
import testkit.ShortTimeout

class AuthProviderClientWithWireMockSpec extends BaseConnectorWithWireMockSpec with ShortTimeout {

  val serviceName = "testServiceName"

  "generateAccessCode request" should {
    val endpoint = "/user-friendly-access-token"
    "return a token when response is OK" in new TestFixture {
      val token = SimpleTokenResponse("12345")

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(token).toString()))
      )

      val result = client.generateAccessCode.futureValue
      result mustBe token
    }

    "generate an exception when response is BAD_REQUEST" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.generateAccessCode.failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "findAuthInfoByUserIds request" should {
    val endpoint = s"/faststream/service/$serviceName/findAuthInfoByIds"
    "return data" in new TestFixture {
      val response = List(UserAuthInfo("userId1", isActive = true, disabled = true, lastAttempt = None, failedAttempts = None))

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"userIds\":[\"userId1\"]}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val userIds = Seq("userId1")
      val result = client.findAuthInfoByUserIds(userIds).futureValue
      result mustBe response
    }

    "handle no data returned" in new TestFixture {
      val response = List.empty[UserAuthInfo]

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"userIds\":[\"userId1\"]}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val userIds = Seq("userId1")
      val result = client.findAuthInfoByUserIds(userIds).futureValue
      result mustBe response
    }
  }

  "findByUserIds request" should {
    val endpoint = s"/faststream/service/$serviceName/findUsersByIds"
    "return data" in new TestFixture {
      val response = Seq(
        Candidate("firstName", "lastName", preferredName = None, "test@test.com", phone = None, "userId", roles = List("candidate"))
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"userIds\":[\"userId1\"]}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val userIds = Seq("userId1")
      val result = client.findByUserIds(userIds).futureValue
      result mustBe response
    }

    "handle no data returned" in new TestFixture {
      val response =  Seq.empty[Candidate]

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"userIds\":[\"userId1\"]}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val userIds = Seq("userId1")
      val result = client.findByUserIds(userIds).futureValue
      result mustBe response
    }
  }

  "findByFirstNameAndLastName request" should {
    val endpoint = s"/faststream/service/$serviceName/findByFirstNameLastName"
    "return data" in new TestFixture {
      val response = Seq(
        Candidate("firstName", "lastName", preferredName = None, "test@test.com", phone = None, "userId", roles = List("candidate"))
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\",\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.findByFirstNameAndLastName("firstName", "lastName", roles = List("candidate")).futureValue
      result mustBe response
    }

    "handle too much data returned" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\",\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(REQUEST_ENTITY_TOO_LARGE))
      )

      val result = client.findByFirstNameAndLastName("firstName", "lastName", roles = List("candidate")).failed.futureValue
      result mustBe a[TooManyResultsException]
    }

    "handle an unexpected response status code" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\",\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.findByFirstNameAndLastName("firstName", "lastName", roles = List("candidate")).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "findByLastName request" should {
    val endpoint = s"/faststream/service/$serviceName/findByLastName"
    "return data" in new TestFixture {
      val response = Seq(
        Candidate("firstName", "lastName", preferredName = None, "test@test.com", phone = None, "userId", roles = List("candidate"))
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.findByLastName("lastName", roles = List("candidate")).futureValue
      result mustBe response
    }

    "handle too much data returned" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(REQUEST_ENTITY_TOO_LARGE))
      )

      val result = client.findByLastName("lastName", roles = List("candidate")).failed.futureValue
      result mustBe a[TooManyResultsException]
    }

    "handle an unexpected response status code" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"lastName\":\"lastName\"}"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.findByLastName("lastName", roles = List("candidate")).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "findByFirstName request" should {
    val endpoint = s"/faststream/service/$serviceName/findByFirstName"
    "return data" in new TestFixture {
      val response = Seq(
        Candidate("firstName", "lastName", preferredName = None, "test@test.com", phone = None, "userId", roles = List("candidate"))
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\"}"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.findByFirstName("firstName", roles = List("candidate")).futureValue
      result mustBe response
    }

    "handle too much data returned" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\"}"))
        .willReturn(aResponse().withStatus(REQUEST_ENTITY_TOO_LARGE))
      )

      val result = client.findByFirstName("firstName", roles = List("candidate")).failed.futureValue
      result mustBe a[TooManyResultsException]
    }

    "handle an unexpected response status code" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("{\"roles\":[\"candidate\"],\"firstName\":\"firstName\"}"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.findByFirstName("firstName", roles = List("candidate")).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "activate token request" should {
    val endpoint = "/activate"
    val email = "test@test.com"
    val token = "12345"
    "handle a response indicating success" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(s"""{\"email\":\"$email\",\"token\":\"$token\",\"service\":\"$serviceName\"}"""))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = clientTDG.activate(email, token).futureValue
      result mustBe unit
    }

    "generate an exception when response indicates token has expired" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(s"""{\"email\":\"$email\",\"token\":\"$token\",\"service\":\"$serviceName\"}"""))
        .willReturn(aResponse().withStatus(GONE))
      )

      val result = clientTDG.activate(email, token).failed.futureValue
      result mustBe a[TokenExpiredException]
    }

    "generate an exception when response indicates the token was not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(s"""{\"email\":\"$email\",\"token\":\"$token\",\"service\":\"$serviceName\"}"""))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = clientTDG.activate(email, token).failed.futureValue
      result mustBe a[TokenEmailPairInvalidException]
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val http = app.injector.instanceOf(classOf[HttpClientV2])

    val mockMicroserviceAppConfig = new MicroserviceAppConfig(mockConfiguration, mockEnvironment) {
      override lazy val userManagementConfig = UserManagementConfig(s"http://localhost:$wireMockPort")
      override lazy val authConfig = AuthConfig(serviceName)
    }

    val client = new AuthProviderClient(http, mockMicroserviceAppConfig)
    //TODO: we need to split out a new test class
    val clientTDG = new AuthProviderClientTDG(http, mockMicroserviceAppConfig)
  }
}
