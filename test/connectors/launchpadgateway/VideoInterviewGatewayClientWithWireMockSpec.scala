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

package connectors.launchpadgateway

import com.github.tomakehurst.wiremock.client.WireMock._
import config.{LaunchpadGatewayConfig, MicroserviceAppConfig, WSHttpT}
import connectors.BaseConnectorWithWireMockSpec
import connectors.launchpadgateway.exchangeobjects.out._
import model.Exceptions.ConnectorException
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.mockito.Mockito.when
import play.api.http.Status._
import play.api.libs.json.Json
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests

class VideoInterviewGatewayClientWithWireMockSpec extends BaseConnectorWithWireMockSpec {

  "registerApplicant request" should {
    val endpoint = "/fset-video-interview-gateway/faststream/register"

    val registerApplicantRequest = RegisterApplicantRequest(
      "test@test.com", "customCandidateId", "firstName", "lastName")

    "handle a response indicating success" in new TestFixture {
      val response = RegisterApplicantResponse("candidateId", "customCandidateId")

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(registerApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.registerApplicant(registerApplicantRequest).futureValue
      result mustBe response
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(registerApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.registerApplicant(registerApplicantRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "inviteApplicant request" should {
    val endpoint = "/fset-video-interview-gateway/faststream/invite"

    val inviteApplicantRequest = InviteApplicantRequest(
      interviewId = 12345, "candidateId", "customInviteId", "http://host/redirectUrl")

    "handle a response indicating success" in new TestFixture {
      val response = InviteApplicantResponse(
        "customInviteId", "candidateId", "customCandidateId", "http://host/testUrl", "deadline")

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(inviteApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.inviteApplicant(inviteApplicantRequest).futureValue
      result mustBe response
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(inviteApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.inviteApplicant(inviteApplicantRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "resetApplicant request" should {
    val endpoint = "/fset-video-interview-gateway/faststream/reset"

    val resetApplicantRequest = ResetApplicantRequest(
      interviewId = 12345, "candidateId", newDeadline = LocalDate.parse("2000-01-31", DateTimeFormat.forPattern("yyyy-MM-dd"))
    )

    "handle a response indicating success" in new TestFixture {
      val response = ResetApplicantResponse("customInviteId", "http://host/testUrl", "deadline")

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.resetApplicant(resetApplicantRequest).futureValue
      result mustBe response
    }

    "handle a response indicating a conflict" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(CONFLICT))
      )

      val result = client.resetApplicant(resetApplicantRequest).failed.futureValue
      result mustBe a[CannotResetPhase3Tests]
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(resetApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.resetApplicant(resetApplicantRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "retakeApplicant request" should {
    val endpoint = "/fset-video-interview-gateway/faststream/retake"

    val retakeApplicantRequest = RetakeApplicantRequest(
      interviewId = 12345, "candidateId", newDeadline = LocalDate.parse("2000-01-31", DateTimeFormat.forPattern("yyyy-MM-dd"))
    )

    "handle a response indicating success" in new TestFixture {
      val response = RetakeApplicantResponse("customInviteId", "http://host/testUrl", "deadline")

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(retakeApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.retakeApplicant(retakeApplicantRequest).futureValue
      result mustBe response
    }

    "handle a response indicating a conflict" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(retakeApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(CONFLICT))
      )

      val result = client.retakeApplicant(retakeApplicantRequest).failed.futureValue
      result mustBe a[CannotResetPhase3Tests]
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(retakeApplicantRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.retakeApplicant(retakeApplicantRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "extendDeadline request" should {
    val endpoint = "/fset-video-interview-gateway/faststream/extend"

    val extendDeadlineRequest = ExtendDeadlineRequest(
      interviewId = 12345, "candidateId", newDeadline = LocalDate.parse("2000-01-31", DateTimeFormat.forPattern("yyyy-MM-dd"))
    )

    "handle a response indicating success" in new TestFixture {
      val response = RetakeApplicantResponse("customInviteId", "http://host/testUrl", "deadline")

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(extendDeadlineRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.extendDeadline(extendDeadlineRequest).futureValue
      result mustBe unit
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(extendDeadlineRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.extendDeadline(extendDeadlineRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val ws = app.injector.instanceOf(classOf[WSHttpT])

    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]
    val mockLaunchpadGatewayConfig = mock[LaunchpadGatewayConfig]

    when(mockMicroserviceAppConfig.launchpadGatewayConfig).thenReturn(mockLaunchpadGatewayConfig)
    when(mockLaunchpadGatewayConfig.url).thenReturn(s"http://localhost:$wireMockPort")

    val client = new LaunchpadGatewayClient(ws, mockMicroserviceAppConfig)
  }
}
