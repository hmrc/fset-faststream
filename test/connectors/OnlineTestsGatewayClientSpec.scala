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

//import config.WSHttp
import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, WSHttpT}
import connectors.ExchangeObjects._
import model.Exceptions.ConnectorException
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{Json, _}
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.Helpers._
import testkit.{ShortTimeout, UnitSpec}
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

class OnlineTestsGatewayClientSpec extends UnitSpec with ShortTimeout {
  val FirstName = "firstName"
  val LastName = "lastName"
  val Email = "emailfsdferr@mailinator.com"
//  val registerApplicant = RegisterApplicant(FirstName, LastName, Email)

//  val CubiksUserId = 112923
//  val registration = Registration(CubiksUserId)
//  val registrationHttpResponse = HttpResponse(OK, Some(Json.toJson(registration)))

  // Invitation data
  val ScheduleId = 1111
  val VerbalAndNumericalAssessmentId = 1
  val VerbalSectionId = 1
  val NumericalSectionId = 2
  val verbalTimeAdjustment = 10
  val numericalTimeAdjustment = 9
  val ScheduleCompletionUrl = "http://localhost/complete?token="
  val ResultsUrl = "http://locahost/resulturl"
  val AccessCode = "ajajfjf"
  val LogonUrl = "http://cubiks.com/logonUrl"
  val AuthenticatedUrl = "http://cubiks/authenticatedUrl"
  val timeAdjustments = TimeAdjustments(VerbalAndNumericalAssessmentId, NumericalSectionId, verbalTimeAdjustment)
//  val inviteApplicant = InviteApplicant(ScheduleId, CubiksUserId, "completeurl.com", None, List(timeAdjustments))
//  val invitation = Invitation(CubiksUserId, Email, AccessCode, LogonUrl, AuthenticatedUrl, ScheduleId)
//  val invitationHttpResponse = HttpResponse(OK, Some(Json.toJson(invitation)))

  // pdf report
  val reportId = 1
  val pdfReport = "pdfReport"
  val pdfReportContent = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20)

  // TODO: cubiks specific
  /*
  "register applicant" should {
    "return a ConnectorException when online test gateway returns HTTP status Bad Gateway" in new GatewayTest {
      mockPost[RegisterApplicant].thenReturn(Future.successful(HttpResponse(BAD_GATEWAY)))
      val result = onlineTestsGatewayClient.registerApplicant(registerApplicant)
      result.failed.futureValue mustBe a[ConnectorException]
    }
    "return an Exception when there is an exception when calling online test gateway" in new GatewayTest {
      mockPost[RegisterApplicant].thenReturn(Future.failed(new Exception))
      val result = onlineTestsGatewayClient.registerApplicant(registerApplicant)
      result.failed.futureValue mustBe an[Exception]
    }
    "register an applicant and return a Registration when successful" in new GatewayTest {
      mockPost[RegisterApplicant].thenReturn(Future.successful(registrationHttpResponse))
      val result = onlineTestsGatewayClient.registerApplicant(registerApplicant)
      result.futureValue.userId must be(CubiksUserId)
    }
  }*/

  // TODO: cubiks delete
  /*
  "invite application" should {
    "return a ConnectorException when online test gateway returns HTTP status Bad Gateway" in new GatewayTest {
      mockPost[InviteApplicant].thenReturn(Future.successful(HttpResponse(BAD_GATEWAY)))
      val result = onlineTestsGatewayClient.inviteApplicant(inviteApplicant)
      result.failed.futureValue mustBe a[ConnectorException]
    }
    "throw an Exception when there is an exception when calling online test gateway" in new GatewayTest {
      mockPost[InviteApplicant].thenReturn(Future.failed(new Exception))
      val result = onlineTestsGatewayClient.inviteApplicant(inviteApplicant)
      result.failed.futureValue mustBe an[Exception]
    }
    "invite an applicant and return an Invitation when successful" in new GatewayTest {
      mockPost[InviteApplicant].thenReturn(Future.successful(invitationHttpResponse))
      val result = onlineTestsGatewayClient.inviteApplicant(inviteApplicant)
      result.futureValue must be(invitation)
    }
  }*/

  trait GatewayTest {
    implicit val hc = HeaderCarrier()
    val mockWSHttp = mock[WSHttpT]

    val samplePDFValue = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20)
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

//    val onlineTestsGatewayClient = new OnlineTestsGatewayClient {
//      override val http = mockWSHttp
//      override val url = "http://localhost"
//    }

    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]
    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    when(mockOnlineTestsGatewayConfig.url).thenReturn("http://localhost")
    when(mockMicroserviceAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    val onlineTestsGatewayClient = new OnlineTestsGatewayClientImpl(
      mockWSHttp,
      mockMicroserviceAppConfig
  )

    def mockPost[T] = {
      when(
        mockWSHttp.POST(anyString(), any[T], any())(any[Writes[T]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
      )
    }
  }
}
