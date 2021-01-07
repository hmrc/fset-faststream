/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{CSRHttp, FrontendAppConfig}
import connectors.SchemeClient.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import connectors.exchange.SelectedSchemes
import javax.inject.{Inject, Singleton}
import models.UniqueIdentifier
import play.api.http.Status._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, NotFoundException, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext

@Singleton
class SchemeClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit ec: ExecutionContext) {
  val url = config.faststreamBackendConfig.url

  def getSchemePreferences(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http.GET[SelectedSchemes](
      s"${url.host}${url.base}/scheme-preferences/$applicationId"
    ).recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => throw new SchemePreferencesNotFound
    }
  }

  def updateSchemePreferences(data: SelectedSchemes)(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/scheme-preferences/$applicationId",
      data
    ).map {
      case x: HttpResponse if x.status == OK => ()
    }.recover {
      case _: BadRequestException => throw new CannotUpdateSchemePreferences
    }
  }
}

object SchemeClient {
  sealed class SchemePreferencesNotFound extends Exception
  sealed class CannotUpdateSchemePreferences extends Exception
}
