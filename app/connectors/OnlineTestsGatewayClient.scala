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

//import config.MicroserviceAppConfig._
import config.{ MicroserviceAppConfig, WSHttpT }
import connectors.ExchangeObjects._
import model.Exceptions.ConnectorException
import model.OnlineTestCommands.Implicits._
import ExchangeObjects.Implicits._
import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.OnlineTestCommands._
import play.api.http.Status._
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@ImplementedBy(classOf[OnlineTestsGatewayClientImpl])
trait OnlineTestsGatewayClient {
  val http: WSHttpT
  val url: String
  val root = "fset-online-tests-gateway"

  // Blank out header carriers for calls to LPG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for whitelisting in the LPG as well (even though they've gone from front -> back -> LPG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier = HeaderCarrier()

  def registerApplicants(batchSize: Int): Future[List[Registration]] = {
    Logger.debug(s"$root registerApplicants E-tray/numeric test GET request - $url/$root/faststream/register/$batchSize")

    http.GET(s"$url/$root/faststream/register/$batchSize").map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root registerApplicants response - ${response.json.toString}")
        response.json.as[List[Registration]]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def registerApplicant(registerApplicant: RegisterApplicant): Future[Registration] = {
    Logger.debug(s"$root registerApplicant POST request, body=${play.api.libs.json.Json.toJson(registerApplicant).toString}")

    http.POST(s"$url/$root/faststream/register", registerApplicant).map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root registerApplicant response - ${response.json.toString}")
        response.json.as[Registration]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def psiRegisterApplicant(request: RegisterCandidateRequest): Future[AssessmentOrderAcknowledgement] = {
    Logger.debug(s"$root psi registerApplicant POST request, body=${Json.toJson(request).toString}")

    http.POST(url = s"$url/$root/faststream/psi-register", request).map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root psiRegisterApplicant response - ${response.json.toString}")
        response.json.as[AssessmentOrderAcknowledgement]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def psiCancelTest(request: CancelCandidateTestRequest): Future[AssessmentCancelAcknowledgementResponse] = {
    Logger.debug(s"cancelTest - $request")

    http.GET(url = s"$url/$root/faststream/psi-cancel-assessment/${request.orderId}").map { response =>
      if (response.status == OK) {
        Logger.debug(s"psiCancelAssessment response - ${response.json.toString}")
        response.json.as[AssessmentCancelAcknowledgementResponse]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def downloadPsiTestResults(reportId: Int): Future[PsiTestResult] = {
    Logger.debug(s"$root downloadPsiTestResults GET request - $url/$root/faststream/psi-results/$reportId")

    http.GET(s"$url/$root/faststream/psi-results/$reportId").map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root downloadPsiTestResults response - ${response.json.toString}")
        response.json.as[PsiTestResult]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def inviteApplicant(inviteApplicant: InviteApplicant): Future[Invitation] = {
    Logger.debug(s"$root inviteApplicant POST request, body=${play.api.libs.json.Json.toJson(inviteApplicant).toString}")

    http.POST(s"$url/$root/faststream/invite", inviteApplicant).map { response =>
      if (response.status == OK) {
        Logger.debug(s"inviteApplicant response - ${response.json.toString}")
        response.json.as[Invitation]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def inviteApplicants(invitations: List[InviteApplicant]): Future[List[Invitation]] = {
    Logger.debug(s"$root inviteApplicants POST request, body=${play.api.libs.json.Json.toJson(invitations).toString}")

    http.POST(s"$url/$root/faststream/batchInvite", invitations).map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root inviteApplicants response - ${response.json.toString}")
        response.json.as[List[Invitation]]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def downloadXmlReport(reportId: Int): Future[TestResult] = {
    Logger.debug(s"$root downloadXmlReport GET request - $url/$root/faststream/report-xml/$reportId")

    http.GET(s"$url/$root/faststream/report-xml/$reportId").map { response =>
      if (response.status == OK) {
        Logger.debug(s"$root downloadXmlReport response - ${response.json.toString}")
        response.json.as[TestResult]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }
}

@Singleton
class OnlineTestsGatewayClientImpl @Inject() (val http: WSHttpT, appConfig: MicroserviceAppConfig) extends OnlineTestsGatewayClient {
  val url: String = appConfig.onlineTestsGatewayConfig.url
}
