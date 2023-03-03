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

import config.{MicroserviceAppConfig, WSHttpT}
import connectors.launchpadgateway.exchangeobjects.out._
import model.Exceptions.ConnectorException
import play.api.http.Status._
import play.api.libs.json.Reads
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LaunchpadGatewayClient @Inject() (http: WSHttpT, config: MicroserviceAppConfig)(implicit ec: ExecutionContext) {
  val url: String = config.launchpadGatewayConfig.url

  // Blank out header carriers for calls to VIG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for allowlisting in the VIG as well (even though they've gone from front -> back -> VIG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier = HeaderCarrier()

  lazy val urlWithPathPrefix = s"$url/fset-video-interview-gateway/faststream"

  def registerApplicant(registerApplicant: RegisterApplicantRequest): Future[RegisterApplicantResponse] =
    http.POST[RegisterApplicantRequest, HttpResponse](s"$urlWithPathPrefix/register", registerApplicant)
      .map(responseAsOrThrow[RegisterApplicantResponse])

  def inviteApplicant(inviteApplicant: InviteApplicantRequest): Future[InviteApplicantResponse] =
    http.POST[InviteApplicantRequest, HttpResponse](s"$urlWithPathPrefix/invite", inviteApplicant)
      .map(responseAsOrThrow[InviteApplicantResponse])

  def resetApplicant(resetApplicant: ResetApplicantRequest): Future[ResetApplicantResponse] =
    http.POST[ResetApplicantRequest, HttpResponse](s"$urlWithPathPrefix/reset", resetApplicant)
      .map {
        case response if response.status == OK => response.json.as[ResetApplicantResponse]
        case response if response.status == CONFLICT => throw new CannotResetPhase3Tests
        case response => throwConnectorException(response)
      }

  def retakeApplicant(retakeApplicant: RetakeApplicantRequest): Future[RetakeApplicantResponse] = {
    http.POST[RetakeApplicantRequest, HttpResponse](s"$urlWithPathPrefix/retake", retakeApplicant)
      .map {
        case response if response.status == OK => response.json.as[RetakeApplicantResponse]
        // TODO: looks like the wrong exception is being thrown?
        case response if response.status == CONFLICT => throw new CannotResetPhase3Tests
        case response => throwConnectorException(response)
      }
  }

  def extendDeadline(extendDeadline: ExtendDeadlineRequest): Future[Unit] =
    http.POST[ExtendDeadlineRequest, HttpResponse](s"$urlWithPathPrefix/extend", extendDeadline).map { response =>
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
