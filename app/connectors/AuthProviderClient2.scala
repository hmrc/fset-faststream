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

import config.MicroserviceAppConfig
import connectors.ExchangeObjects.{Candidate, UserAuthInfo}
import model.Exceptions.ConnectorException
import model.exchange.SimpleTokenResponse
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// Created to fix Scala 3 migration circular dependency issue
@Singleton
class AuthProviderClient2 @Inject() (http: HttpClientV2, config: MicroserviceAppConfig)(implicit ec: ExecutionContext) {

  private lazy val ServiceName = config.authConfig.serviceName
  private lazy val url = config.userManagementConfig.url

  def findByUserIds(userIds: Seq[String])(implicit hs: HeaderCarrier): Future[Seq[Candidate]] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/faststream/service/$ServiceName/findUsersByIds")
      .withBody(Json.toJson(Map("userIds" -> userIds)))
      .execute[HttpResponse]
      .map ( response => response.json.as[List[Candidate]] )
  }

  def generateAccessCode(implicit hc: HeaderCarrier): Future[SimpleTokenResponse] = {
    http.get(url"$url/user-friendly-access-token")
      .execute[SimpleTokenResponse]
      .recover {
        case _ => throw new ConnectorException(s"Bad response received when getting access code")
      }
  }
}
