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

package connectors

import config.CSRHttp
import connectors.SchemeClient.CannotFindSelection
import models.UniqueIdentifier
import models.frameworks.{ Alternatives, LocationAndSchemeSelection, Preference, Region }
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.{ HeaderCarrier, HttpResponse, NotFoundException }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SchemeClient {

  import config.FrontendAppConfig.fasttrackConfig._

  val http: CSRHttp

  def getAvailableFrameworksWithLocations(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[List[Region]] =
    http.GET(s"${url.host}${url.base}/frameworks-available-to-application/$applicationId").map(_.json.as[List[Region]])

  def getSelection(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[LocationAndSchemeSelection] =
    http.GET(s"${url.host}${url.base}/framework-preference/$applicationId").map(_.json.as[LocationAndSchemeSelection]).recover {
      case e: NotFoundException => throw new CannotFindSelection()
    }

  def updateFirstPref(data: Preference)(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.PUT(
      s"${url.host}${url.base}/framework-preference/first/$applicationId",
      data
    ).map {
        case x: HttpResponse if x.status == CREATED => ()
        case x: HttpResponse if x.status == OK => ()
      }
  }

  def updateSecondPref(data: Preference)(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/framework-preference/second/$applicationId",
      data
    ).map {
        case x: HttpResponse if x.status == OK => ()
      }
  }

  case class SecondPreferenceIntention(secondPreferenceIntended: Boolean)

  implicit val jsonFormatPref = Json.format[SecondPreferenceIntention]

  def updateNoSecondPref(noSecPref: Boolean)(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/framework-preference/second/intention/$applicationId",
      SecondPreferenceIntention(noSecPref)
    ).map {
        case x: HttpResponse if x.status == OK => ()
      }
  }

  def updateAlternatives(data: Alternatives)(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    http.PUT(
      s"${url.host}${url.base}/framework-preference/alternatives/$applicationId",
      data
    ).map {
        case x: HttpResponse if x.status == OK => ()
      }
  }

}

object SchemeClient {

  sealed class CannotFindSelection extends Exception

}
