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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config._
import connectors.UserManagementClient._
import connectors.exchange._
import models.UniqueIdentifier
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.UpstreamErrorResponse

class UserManagementClientWithWireMockSpec extends BaseConnectorWithWireMockSpec {

  val urlPrefix = "faststream"
  val serviceName = "testServiceName"
  val userId = UniqueIdentifier("5efd6e38-be9e-4007-a15f-44acc4d75ec5")

  "register" should {
    val endpoint = s"/$urlPrefix/add"

    val email = "joeblogs@test.com"
    val password = "password"
    val firstName = "Joe"
    val lastName = "Blogs"
    val addUserRequest = AddUserRequest(email.toLowerCase, password, firstName, lastName, List("candidate"), serviceName)

    "handle a response indicating success" in new TestFixture {
      val response = UserResponse(
        firstName,
        lastName,
        preferredName = None,
        isActive = true,
        userId,
        email,
        disabled = false,
        lockStatus = "",
        roles = List("candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(addUserRequest).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.register(email, password, firstName, lastName).futureValue
      result mustBe response
    }

    "handle a response indicating a conflict" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(addUserRequest).toString))
        .willReturn(aResponse().withStatus(CONFLICT))
      )

      val result = client.register(email, password, firstName, lastName).failed.futureValue
      result mustBe an[EmailTakenException]
    }

    "throw an UpstreamErrorResponse when dealing with a response indicating an internal server error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(addUserRequest).toString))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.register(email, password, firstName, lastName).failed.futureValue
      result mustBe a[UpstreamErrorResponse]
    }
  }

  "signIn" should {
    val endpoint = s"/$urlPrefix/authenticate"

    val email = "joeblogs@test.com"
    val password = "password"
    val firstName = "Joe"
    val lastName = "Blogs"
    val signInRequest = SignInRequest(email, password, serviceName)

    "handle a response indicating success" in new TestFixture {
      val response = UserResponse(
        firstName,
        lastName,
        preferredName = None,
        isActive = true,
        userId,
        email,
        disabled = false,
        lockStatus = "",
        roles = List("candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(signInRequest).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.signIn(email, password).futureValue
      result mustBe response
    }

    "handle a response indicating unauthorized" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(signInRequest).toString))
        .willReturn(aResponse().withStatus(UNAUTHORIZED))
      )

      val result = client.signIn(email, password).failed.futureValue
      result mustBe an[InvalidCredentialsException]
    }

    "handle a response indicating the user is not a candidate" in new TestFixture {
      val response = UserResponse(
        firstName,
        lastName,
        preferredName = None,
        isActive = true,
        userId,
        email,
        disabled = false,
        lockStatus = "",
        roles = List("not-a-candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(signInRequest).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.signIn(email, password).failed.futureValue
      result mustBe an[InvalidRoleException]
    }
  }

  "activate" should {
    val endpoint = s"/activate"

    val email = "joeblogs@test.com"
    val token = "12345"
    val activateEmailRequest = ActivateEmailRequest(email, token, serviceName)

    "handle a response indicating success" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(activateEmailRequest).toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.activate(email, token).futureValue
      result mustBe unit
    }

    "handle a response indicating gone" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(activateEmailRequest).toString))
        .willReturn(aResponse().withStatus(GONE))
      )

      val result = client.activate(email, token).failed.futureValue
      result mustBe a[TokenExpiredException]
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(activateEmailRequest).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.activate(email, token).failed.futureValue
      result mustBe a[TokenEmailPairInvalidException]
    }
  }

  "resendActivationCode" should {
    val endpoint = s"/resend-activation-code"

    val email = "joeblogs@test.com"
    val resendActivationTokenRequest = ResendActivationTokenRequest(email, serviceName)

    "handle a response indicating success" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resendActivationTokenRequest).toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.resendActivationCode(email).futureValue
      result mustBe unit
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resendActivationTokenRequest).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.resendActivationCode(email).failed.futureValue
      result mustBe an[InvalidEmailException]
    }
  }

  "sendResetPwdCode" should {
    val endpoint = s"/$urlPrefix/send-reset-password-code"

    val email = "joeblogs@test.com"
    val sendPasswordCodeRequest = SendPasswordCodeRequest(email, serviceName)

    "handle a response indicating success" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(sendPasswordCodeRequest).toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.sendResetPwdCode(email).futureValue
      result mustBe unit
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(sendPasswordCodeRequest).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.sendResetPwdCode(email).failed.futureValue
      result mustBe an[InvalidEmailException]
    }
  }

  "resetPasswd" should {
    val endpoint = s"/$urlPrefix/reset-password"

    val email = "joeblogs@test.com"
    val token = "12345"
    val newPassword = "password"
    val resetPasswordRequest = ResetPasswordRequest(email, token, newPassword, serviceName)

    "handle a response indicating success" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetPasswordRequest).toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.resetPasswd(email, token, newPassword).futureValue
      result mustBe unit
    }

    "handle a response indicating gone" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetPasswordRequest).toString))
        .willReturn(aResponse().withStatus(GONE))
      )

      val result = client.resetPasswd(email, token, newPassword).failed.futureValue
      result mustBe a[TokenExpiredException]
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetPasswordRequest).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.resetPasswd(email, token, newPassword).failed.futureValue
      result mustBe a[TokenEmailPairInvalidException]
    }
  }

  "updateDetails" should {
    val endpoint = s"/$urlPrefix/service/$serviceName/details/$userId"

    val firstName = "firstName"
    val lastName = "lastName"
    val preferredName = None
    val updateDetails = UpdateDetails(firstName, lastName, preferredName, serviceName)

    "handle a response indicating success" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(updateDetails).toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.updateDetails(userId, firstName, lastName, preferredName).futureValue
      result mustBe unit
    }
  }

  "failedLogin" should {
    val endpoint = s"/$urlPrefix/failedAttempt"

    val email = "joeblogs@test.com"
    val emailWrapper = EmailWrapper(email, serviceName)

    "handle a response indicating success" in new TestFixture {
      val response = UserResponse(
        "firstName",
        "lastName",
        preferredName = None,
        isActive = true,
        userId,
        email,
        disabled = false,
        lockStatus = "",
        roles = List("candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(put(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(emailWrapper).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.failedLogin(email).futureValue
      result mustBe response
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(emailWrapper).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.failedLogin(email).failed.futureValue
      result mustBe an[InvalidCredentialsException]
    }

    "handle a response indicating locked" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(emailWrapper).toString))
        .willReturn(aResponse().withStatus(LOCKED))
      )

      val result = client.failedLogin(email).failed.futureValue
      result mustBe an[AccountLockedOutException]
    }
  }

  "find" should {
    val endpoint = s"/$urlPrefix/find"

    val email = "joeblogs@test.com"
    val emailWrapper = EmailWrapper(email, serviceName)

    "handle a response indicating success" in new TestFixture {
      val response = UserResponse(
        "firstName",
        "lastName",
        preferredName = None,
        isActive = true,
        userId,
        email,
        disabled = false,
        lockStatus = "",
        roles = List("candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(emailWrapper).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.find(email).futureValue
      result mustBe response
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(emailWrapper).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.find(email).failed.futureValue
      result mustBe an[InvalidCredentialsException]
    }
  }

  "findByUserId" should {
    val endpoint = s"/$urlPrefix/service/$serviceName/findUserById"

    val findByUserIdRequest = FindByUserIdRequest(userId)

    "handle a response indicating success" in new TestFixture {
      val response = UserResponse(
        "firstName",
        "lastName",
        preferredName = None,
        isActive = true,
        userId,
        "joeblogs@test.com",
        disabled = false,
        lockStatus = "",
        roles = List("candidate"),
        serviceName,
        phoneNumber = None
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(findByUserIdRequest).toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString))
      )

      val result = client.findByUserId(userId).futureValue
      result mustBe response
    }

    "handle a response indicating not found" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(findByUserIdRequest).toString))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.findByUserId(userId).failed.futureValue
      result mustBe an[InvalidCredentialsException]
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val mockConfig = new FrontendAppConfig(mockConfiguration, mockEnvironment) {
      override lazy val userManagementConfig = UserManagementConfig(UserManagementUrl(s"http://localhost:$wireMockPort"))
      override lazy val authConfig = AuthConfig(serviceName)
    }
    val ws = app.injector.instanceOf(classOf[WSClient])
    val http = new CSRHttp(ws, app)
    val client = new UserManagementClient(mockConfig, http)
  }
}
