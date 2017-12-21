/*
 * Copyright 2017 HM Revenue & Customs
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

import config.MicroserviceAppConfig._
import _root_.config.WSHttp
import connectors.launchpadgateway.exchangeobjects.out._
import model.Exceptions.ConnectorException
import play.api.http.Status._
import play.api.libs.json.Reads
import _root_.services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, Upstream4xxResponse }

object LaunchpadGatewayClient extends LaunchpadGatewayClient {
  val http: WSHttp = WSHttp
  val url = launchpadGatewayConfig.url
}

trait LaunchpadGatewayClient {
  val http: WSHttp
  val url: String

  // Blank out header carriers for calls to LPG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for whitelisting in the LPG as well (even though they've gone from front -> back -> LPG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier = HeaderCarrier()

  lazy val urlWithPathPrefix = s"$url/fset-launchpad-gateway/faststream"

  def registerApplicant(registerApplicant: RegisterApplicantRequest): Future[RegisterApplicantResponse] =
    http.POST(s"$urlWithPathPrefix/register", registerApplicant).map(responseAsOrThrow[RegisterApplicantResponse])

  def inviteApplicant(inviteApplicant: InviteApplicantRequest): Future[InviteApplicantResponse] =
    http.POST(s"$urlWithPathPrefix/invite", inviteApplicant).map(responseAsOrThrow[InviteApplicantResponse])

  def resetApplicant(resetApplicant: ResetApplicantRequest): Future[ResetApplicantResponse] =
    http.POST(s"$urlWithPathPrefix/reset", resetApplicant).map(responseAsOrThrow[ResetApplicantResponse]).recover {
      case e: Upstream4xxResponse if (e.upstreamResponseCode == CONFLICT) => throw new CannotResetPhase3Tests
      case t: Throwable => throw t
    }

  def retakeApplicant(retakeApplicant: RetakeApplicantRequest): Future[RetakeApplicantResponse] = {
    http.POST(s"$urlWithPathPrefix/retake", retakeApplicant).map(responseAsOrThrow[RetakeApplicantResponse]).recover {
      case e: Upstream4xxResponse if (e.upstreamResponseCode == CONFLICT) => throw new CannotResetPhase3Tests
      case t: Throwable => throw t
    }
  }

  def extendDeadline(extendDeadline: ExtendDeadlineRequest): Future[Unit] =
    http.POST(s"$urlWithPathPrefix/extend", extendDeadline).map { response =>
      if (response.status != OK) {
        throw new ConnectorException(s"There was a general problem connecting with the Launchpad Gateway. HTTP status " +
          s"was ${response.status} and response was ${response.body}")
      }
    }

  private def responseAsOrThrow[A](response: HttpResponse)(implicit jsonFormat: Reads[A]) = {
    response.status match {
      case OK => response.json.as[A]
      case _ => throw new ConnectorException(s"There was a general problem connecting with the Launchpad Gateway. HTTP status " +
        s"was ${response.status} and response was ${response.body}")
    }
  }
}
