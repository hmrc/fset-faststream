/*
 * Copyright 2019 HM Revenue & Customs
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

import config.CSRHttp
import connectors.SchemeClient.{ CannotUpdateSchemePreferences, SchemePreferencesNotFound }
import connectors.exchange.SelectedSchemes
import models.UniqueIdentifier
import play.api.http.Status._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.{ BadRequestException, HeaderCarrier, HttpResponse, NotFoundException }

trait SchemeClient {

  val http: CSRHttp

  import config.FrontendAppConfig.faststreamConfig._

  def getSchemePreferences(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.GET(
      s"${url.host}${url.base}/scheme-preferences/$applicationId"
    ).map(
      httpResponse => httpResponse.json.as[SelectedSchemes]
    ).recover {
      case e: NotFoundException => throw new SchemePreferencesNotFound
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

object SchemeClient extends SchemeClient {

  override val http: CSRHttp = CSRHttp

  sealed class SchemePreferencesNotFound extends Exception

  sealed class CannotUpdateSchemePreferences extends Exception
}
