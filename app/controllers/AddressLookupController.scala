/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import config.{FrontendAppConfig, SecurityEnvironment}
import connectors.addresslookup.AddressLookupClient
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import security.Roles.EditPersonalDetailsAndContinueRole
import security.SilhouetteComponent
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.ExecutionContext
import helpers.NotificationTypeHelper

@Singleton
class AddressLookupController  @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  addressLookupClient: AddressLookupClient)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {
  import notificationTypeHelper._

  def addressLookupByPostcode(postcode: String): Action[AnyContent] = CSRSecureAction(EditPersonalDetailsAndContinueRole) {
    implicit request => implicit cachedData =>
    val decoded = java.net.URLDecoder.decode(postcode, "UTF8")
    addressLookupClient.findByPostcode(decoded, None).map {
      case head :: tail => Ok(Json.toJson(head::tail))
      case Nil => NotFound
    }.recover {
      case e: BadRequestException =>
        Logger.debug(s"Postcode lookup service returned ${e.getMessage} for postcode $postcode", e)
        BadRequest
    }
  }

  def addressLookupById(id: String): Action[AnyContent] = CSRSecureAction(EditPersonalDetailsAndContinueRole) {
    implicit request => implicit cachedData =>
    addressLookupClient.findByAddressId(id, None).map( address => Ok(Json.toJson(address)) )
  }
}
