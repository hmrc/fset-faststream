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

import config.MicroserviceAppConfig
import connectors.launchpadgateway.exchangeobjects.out.*
import model.Exceptions.ConnectorException
import play.api.http.Status.*
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.writeableOf_JsValue
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LaunchpadGatewayClient @Inject()(http: HttpClientV2, config: MicroserviceAppConfig)(implicit ec: ExecutionContext) {
  val url: String = config.launchpadGatewayConfig.url

  // Blank out header carriers for calls to VIG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for allowlisting in the VIG as well (even though they've gone from front -> back -> VIG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier: HeaderCarrier = HeaderCarrier()

  lazy val urlWithPathPrefix = s"$url/fset-video-interview-gateway/faststream"

  def registerApplicant(registerApplicant: RegisterApplicantRequest): Future[RegisterApplicantResponse] =
    http.post(url"$urlWithPathPrefix/register")
      .withBody(Json.toJson( registerApplicant))
      .execute[HttpResponse]
      .map(responseAsOrThrow[RegisterApplicantResponse])

  def inviteApplicant(inviteApplicant: InviteApplicantRequest): Future[InviteApplicantResponse] =
    http.post(url"$urlWithPathPrefix/invite")
      .withBody(Json.toJson(inviteApplicant))
      .execute[HttpResponse]
      .map(responseAsOrThrow[InviteApplicantResponse])

  def resetApplicant(resetApplicant: ResetApplicantRequest): Future[ResetApplicantResponse] =
    http.post(url"$urlWithPathPrefix/reset")
      .withBody(Json.toJson(resetApplicant))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => response.json.as[ResetApplicantResponse]
        case response if response.status == CONFLICT => throw new CannotResetPhase3Tests
        case response => throwConnectorException(response)
      }

  def retakeApplicant(retakeApplicant: RetakeApplicantRequest): Future[RetakeApplicantResponse] = {
    http.post(url"$urlWithPathPrefix/retake")
      .withBody(Json.toJson(retakeApplicant))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => response.json.as[RetakeApplicantResponse]
        // TODO: looks like the wrong exception is being thrown?
        case response if response.status == CONFLICT => throw new CannotResetPhase3Tests
        case response => throwConnectorException(response)
      }
  }

  def extendDeadline(extendDeadline: ExtendDeadlineRequest): Future[Unit] =
    http.post(url"$urlWithPathPrefix/extend")
      .withBody(Json.toJson(extendDeadline))
      .execute[HttpResponse]
      .map { response =>
      if (response.status != OK) { throwConnectorException(response) }
    }

  private def responseAsOrThrow[A](response: HttpResponse)(implicit jsonFormat: Reads[A]) = {
    response.status match {
      case OK => response.json.as[A]
      case _ => throwConnectorException(response)
    }
  }

  private def throwConnectorException(response: HttpResponse) = {
    throw new ConnectorException(s"There was a general problem connecting with the Video Interview Gateway. HTTP status " +
      s"was ${response.status} and response body was ${response.body}")
  }
}
