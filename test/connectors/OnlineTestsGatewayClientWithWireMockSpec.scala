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
import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, WSHttpT}
import connectors.ExchangeObjects._
import model.Exceptions.ConnectorException
import model.OnlineTestCommands.PsiTestResult
import org.mockito.Mockito.when
import play.api.http.Status._
import play.api.libs.json.Json

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OnlineTestsGatewayClientWithWireMockSpec extends BaseConnectorWithWireMockSpec {

  "psiRegisterApplicant request" should {
    val endpoint = "/fset-online-tests-gateway/faststream/psi-register"

    val registerCandidateRequest = RegisterCandidateRequest(inventoryId = "inventoryId",
      orderId = "orderId",
      accountId = "accountId",
      preferredName = "preferredName",
      lastName = "lastName",
      redirectionUrl = "http://localhost/redirectionUrl",
      assessmentId = "assessmentId",
      reportId = "reportId",
      normId = "normId",
      adjustment = None)

    "handle a response indicating success" in new TestFixture {
      val response = AssessmentOrderAcknowledgement(
        "customerId",
        "receiptId",
        "orderId",
        "http://host/testLaunchUrl",
        "status",
        "statusDetails",
        statusDate = LocalDate.parse("2000-01-31", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      )

      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(registerCandidateRequest).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.psiRegisterApplicant(registerCandidateRequest).futureValue
      result mustBe response
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing(Json.toJson(registerCandidateRequest).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.psiRegisterApplicant(registerCandidateRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "psiCancelTest" should {
    val orderId = "orderId"
    val endpoint = s"/fset-online-tests-gateway/faststream/psi-cancel-assessment/$orderId"
    val cancelCandidateTestRequest = CancelCandidateTestRequest(orderId)

    "handle a response indicating success" in new TestFixture {
      val response = AssessmentCancelAcknowledgementResponse(
        "status", "details", statusDate = LocalDate.parse("2000-01-31", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      )

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.psiCancelTest(cancelCandidateTestRequest).futureValue
      result mustBe response
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.psiCancelTest(cancelCandidateTestRequest).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  "downloadPsiTestResults" should {
    val reportId = 12345
    val endpoint = s"/fset-online-tests-gateway/faststream/psi-results/$reportId"

    "handle a response indicating success" in new TestFixture {
      val response = PsiTestResult("status", tScore = 80.0, raw = 40.0)

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.downloadPsiTestResults(reportId).futureValue
      result mustBe response
    }

    "handle a response indicating an error" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.downloadPsiTestResults(reportId).failed.futureValue
      result mustBe a[ConnectorException]
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val ws = app.injector.instanceOf(classOf[WSHttpT])

    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]
    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]

    when(mockMicroserviceAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)
    when(mockOnlineTestsGatewayConfig.url).thenReturn(s"http://localhost:$wireMockPort")

    val client = new OnlineTestsGatewayClientImpl(ws, mockMicroserviceAppConfig)
  }
}
