/*
 * Copyright 2016 HM Revenue & Customs
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

import _root_.config.WSHttp
import config.MicroserviceAppConfig._
import connectors.launchpadgateway.exchangeobjects._
import model.Exceptions.ConnectorException
import play.api.http.Status._
import play.api.libs.json.Reads
import uk.gov.hmrc.play.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LaunchpadGatewayClient extends LaunchpadGatewayClient {
  val http: WSHttp = WSHttp
  val url = launchpadGatewayConfig.url
}

trait LaunchpadGatewayClient {
  val http: WSHttp
  val url: String

  lazy val urlWithPathPrefix = s"$url/fset-launchpad-gateway/faststream"

  def registerApplicant(registerApplicant: RegisterApplicantRequest)(implicit hc: HeaderCarrier): Future[RegisterApplicantResponse] =
    http.POST(s"$urlWithPathPrefix/register", registerApplicant).map(responseAsOrThrow[RegisterApplicantResponse])

  def inviteApplicant(inviteApplicant: InviteApplicantRequest)(implicit hc: HeaderCarrier): Future[InviteApplicantResponse] =
    http.POST(s"$urlWithPathPrefix/invite", inviteApplicant).map(responseAsOrThrow[InviteApplicantResponse])

  private def responseAsOrThrow[A](response: HttpResponse)(implicit jsonFormat: Reads[A]) = {
    if (response.status == OK) {
      response.json.as[A]
    } else {
      throw new ConnectorException(s"There was a general problem connecting with the Launchpad Gateway. HTTP status " +
        s"was ${response.status} and response was ${response.body}")
    }
  }
}


