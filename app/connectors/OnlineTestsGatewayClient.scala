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

import com.google.inject.ImplementedBy
import config.MicroserviceAppConfig
import connectors.ExchangeObjects._
import model.Exceptions.ConnectorException
import model.OnlineTestCommands._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OnlineTestsGatewayClientImpl])
trait OnlineTestsGatewayClient extends Logging {
  val http: HttpClientV2
  val url: String
  val root = "fset-online-tests-gateway"

  // Blank out header carriers for calls to LPG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for whitelisting in the LPG as well (even though they've gone from front -> back -> LPG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier: HeaderCarrier = HeaderCarrier()

  def psiRegisterApplicant(request: RegisterCandidateRequest)(implicit ec: ExecutionContext): Future[AssessmentOrderAcknowledgement] = {
    logger.debug(s"$root psi registerApplicant POST request, body=${Json.toJson(request).toString}")

    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$root/faststream/psi-register")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        if (response.status == OK) {
          logger.debug(s"$root psiRegisterApplicant response - ${response.json.toString}")
          response.json.as[AssessmentOrderAcknowledgement]
        } else {
          throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
        }
      }
  }

  def psiCancelTest(request: CancelCandidateTestRequest)(implicit ec: ExecutionContext): Future[AssessmentCancelAcknowledgementResponse] = {
    logger.debug(s"cancelTest - $request")

    http.get(url"$url/$root/faststream/psi-cancel-assessment/${request.orderId}")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == OK) {
          logger.debug(s"psiCancelAssessment response - ${response.json.toString}")
          response.json.as[AssessmentCancelAcknowledgementResponse]
        } else {
          throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
        }
      }
  }

  def downloadPsiTestResults(reportId: Int)(implicit ec: ExecutionContext): Future[PsiTestResult] = {
    logger.debug(s"$root downloadPsiTestResults GET request - $url/$root/faststream/psi-results/$reportId")

    http.get(url"$url/$root/faststream/psi-results/$reportId")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == OK) {
          logger.debug(s"$root downloadPsiTestResults response - ${response.json.toString}")
          response.json.as[PsiTestResult]
        } else {
          throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
        }
      }
  }
}

@Singleton
class OnlineTestsGatewayClientImpl @Inject() (val http: HttpClientV2, appConfig: MicroserviceAppConfig)(
  implicit ec: ExecutionContext) extends OnlineTestsGatewayClient {
  val url: String = appConfig.onlineTestsGatewayConfig.url
}
