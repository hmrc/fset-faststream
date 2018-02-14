/*
 * Copyright 2018 HM Revenue & Customs
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

import config.MicroserviceAppConfig._
import _root_.config.WSHttp
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, RegisterApplicant, Registration }
import model.Exceptions.ConnectorException
import model.OnlineTestCommands.Implicits._
import model.OnlineTestCommands._
import play.api.http.Status._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait CubiksGatewayClient {
  val http: WSHttp
  val url: String
  val root = "fset-online-tests-gateway"

  // Blank out header carriers for calls to LPG. Passing on someone's true-client-ip header will cause them to be reassessed
  // for whitelisting in the LPG as well (even though they've gone from front -> back -> LPG), which leads to undesirable behaviour.
  implicit def blankedHeaderCarrier = HeaderCarrier()

  def registerApplicants(batchSize: Int): Future[List[Registration]] = {
    http.GET(s"$url/$root/faststream/register/$batchSize").map { response =>
      if (response.status == OK) {
        response.json.as[List[Registration]]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def registerApplicant(registerApplicant: RegisterApplicant): Future[Registration] = {
    http.POST(s"$url/$root/faststream/register", registerApplicant).map { response =>
      if (response.status == OK) {
        response.json.as[Registration]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def inviteApplicant(inviteApplicant: InviteApplicant): Future[Invitation] = {
    http.POST(s"$url/$root/faststream/invite", inviteApplicant).map { response =>
      if (response.status == OK) {
        response.json.as[Invitation]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def inviteApplicants(invitations: List[InviteApplicant]): Future[List[Invitation]] =
    http.POST(s"$url/$root/faststream/batchInvite", invitations).map { response =>
      if (response.status == OK) {
        response.json.as[List[Invitation]]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }

  def downloadXmlReport(reportId: Int): Future[TestResult] = {
    http.GET(s"$url/$root/faststream/report-xml/$reportId").map { response =>
      if (response.status == OK) {
        response.json.as[TestResult]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to Online Tests Gateway. HTTP response was $response")
      }
    }
  }
}

object CubiksGatewayClient extends CubiksGatewayClient {
  val http: WSHttp = WSHttp
  val url: String = cubiksGatewayConfig.url
}
