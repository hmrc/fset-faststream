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

package connectors.paritygateway

import config.MicroserviceAppConfig._
import _root_.config.WSHttp
import model.Exceptions.ConnectorException
import play.api.http.Status._
import play.api.libs.json.JsObject
import uk.gov.hmrc.play.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ParityGatewayClient extends ParityGatewayClient {
  val http: WSHttp = WSHttp
  val url = parityGatewayConfig.url
}

trait ParityGatewayClient {
  val http: WSHttp
  val url: String

  implicit def blankedHeaderCarrier = new HeaderCarrier()

  lazy val urlWithPathPrefix = s"$url/fset-parity-gateway/faststream"

  def createExport(json: JsObject): Future[Unit] =
    http.POST(s"$urlWithPathPrefix/create", json).map(validResponseOrThrow)

  def updateExport(json: JsObject): Future[Unit] =
    http.POST(s"$urlWithPathPrefix/update", json).map(validResponseOrThrow)

  private def validResponseOrThrow(response: HttpResponse) = {
    if (response.status != OK) {
      throw new ConnectorException(s"There was a general problem connecting with the Parity Gateway. HTTP status " +
        s"was ${response.status} and response was ${response.body}")
    }
  }
}
