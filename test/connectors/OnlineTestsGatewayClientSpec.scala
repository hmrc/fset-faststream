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

import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, WSHttpT}
import connectors.ExchangeObjects._
import model.Exceptions.ConnectorException
import model.OnlineTestCommands.PsiTestResult
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.test.Helpers._
import testkit.{ShortTimeout, UnitSpec}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class OnlineTestsGatewayClientSpec extends UnitSpec with ShortTimeout {

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

  val cancelCandidateTestRequest = CancelCandidateTestRequest("orderId")

  val reportId = 1

  "register applicant" should {
    "return a ConnectorException when online test gateway returns HTTP status Bad Gateway" in new GatewayTest {
      mockPost[RegisterCandidateRequest].thenReturn(Future.successful(HttpResponse.apply(BAD_GATEWAY, "Test error")))
      val result = onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest)
      result.failed.futureValue mustBe a[ConnectorException]
    }

    "return an exception when there is an exception when calling online test gateway" in new GatewayTest {
      mockPost[RegisterCandidateRequest].thenReturn(Future.failed(new Exception))
      val result = onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest)
      result.failed.futureValue mustBe an[Exception]
    }

    "return an AssessmentOrderAcknowledgement when successful" in new GatewayTest {
      val assessmentOrderAcknowledgement = AssessmentOrderAcknowledgement(
        "customerId",
        "receiptId",
        "orderId",
        "http://host/testLaunchUrl",
        "status",
        "statusDetails",
        statusDate = LocalDate.now()
      )
      val assessmentOrderAcknowledgementHttpResponse = HttpResponse.apply(OK, Json.toJson(assessmentOrderAcknowledgement).toString())

      mockPost[RegisterCandidateRequest].thenReturn(Future.successful(assessmentOrderAcknowledgementHttpResponse))
      val result = onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest)
      result.futureValue mustBe assessmentOrderAcknowledgement
    }
  }

  "cancel test" should {
    "return a ConnectorException when online test gateway returns HTTP status Bad Gateway" in new GatewayTest {
      mockGet[HttpResponse].thenReturn(Future.successful(HttpResponse.apply(BAD_GATEWAY, "Test error")))
      val result = onlineTestsGatewayClient.psiCancelTest(cancelCandidateTestRequest)
      result.failed.futureValue mustBe a[ConnectorException]
    }

    "return an exception when there is an exception when calling online test gateway" in new GatewayTest {
      mockGet[HttpResponse].thenReturn(Future.failed(new Exception))
      val result = onlineTestsGatewayClient.psiCancelTest(cancelCandidateTestRequest)
      result.failed.futureValue mustBe an[Exception]
    }

    "return an AssessmentCancelAcknowledgementResponse when successful" in new GatewayTest {
      val assessmentCancelAcknowledgementResponse = AssessmentCancelAcknowledgementResponse("status", "details", statusDate = LocalDate.now())
      val assessmentCancelAcknowledgementHttpResponse = HttpResponse.apply(OK, Json.toJson(assessmentCancelAcknowledgementResponse).toString())

      mockGet[HttpResponse].thenReturn(Future.successful(assessmentCancelAcknowledgementHttpResponse))
      val result = onlineTestsGatewayClient.psiCancelTest(cancelCandidateTestRequest)
      result.futureValue mustBe assessmentCancelAcknowledgementResponse
    }
  }

  "download psi test results" should {
    "return a ConnectorException when online test gateway returns HTTP status Bad Gateway" in new GatewayTest {
      mockGet[HttpResponse].thenReturn(Future.successful(HttpResponse.apply(BAD_GATEWAY, "Test error")))
      val result = onlineTestsGatewayClient.downloadPsiTestResults(reportId)
      result.failed.futureValue mustBe a[ConnectorException]
    }

    "return an exception when there is an exception when calling online test gateway" in new GatewayTest {
      mockGet[HttpResponse].thenReturn(Future.failed(new Exception))
      val result = onlineTestsGatewayClient.downloadPsiTestResults(reportId)
      result.failed.futureValue mustBe an[Exception]
    }

    "return a PsiTestResult when successful" in new GatewayTest {
      val testResult = PsiTestResult("status", tScore = 80.0, raw = 40.0)
      val testResultHttpResponse = HttpResponse.apply(OK, Json.toJson(testResult).toString())

      mockGet[HttpResponse].thenReturn(Future.successful(testResultHttpResponse))
      val result = onlineTestsGatewayClient.downloadPsiTestResults(reportId)
      result.futureValue mustBe testResult
    }
  }

  trait GatewayTest {
//    val samplePDFValue = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20)
/*
    def setupPDFWSMock() = {
      when(mockWSHttp.playWS).thenReturn(
        MockWS {
          case (GET, "http://localhost/csr-cubiks-gateway/report-pdf/44444") => Action {
            BadGateway("The gateway is very naughty.")
          }
          case (GET, "http://localhost/csr-cubiks-gateway/report-pdf/55555") => Action {
            throw new Exception()
            Ok("This will never be reached")
          }
          case (GET, "http://localhost/csr-cubiks-gateway/report-pdf/66666") => Action {
            Ok(samplePDFValue)
          }
        }
      )
    }*/

    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]
    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    when(mockOnlineTestsGatewayConfig.url).thenReturn("http://localhost")
    when(mockMicroserviceAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    val mockWSHttp = mock[WSHttpT]
    val onlineTestsGatewayClient = new OnlineTestsGatewayClientImpl(mockWSHttp, mockMicroserviceAppConfig)

    def mockPost[T] = {
      when(
        mockWSHttp.POST(anyString(), any[T], any())(any[Writes[T]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
      )
    }

    def mockGet[T] = {
      when(
        mockWSHttp.GET(anyString(), any(), any())(any[HttpReads[T]], any[HeaderCarrier], any[ExecutionContext])
      )
    }
  }
}
