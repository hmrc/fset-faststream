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

package controllers

import com.mohiva.play.silhouette.api.Silhouette
import connectors.ApplicationClient
import connectors.addresslookup.AddressLookupClient
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import security.Roles.EditPersonalDetailsAndContinueRole
import security.{ SecurityEnvironment, SilhouetteComponent }
import uk.gov.hmrc.http.BadRequestException

abstract class AddressLookupController(addressLookupClient: AddressLookupClient,
  applicationClient: ApplicationClient
) extends BaseController {

  def addressLookupByPostcode(postcode: String): Action[AnyContent] = CSRSecureAction(EditPersonalDetailsAndContinueRole) {
    implicit request => implicit cachedData =>
    val decoded = java.net.URLDecoder.decode(postcode, "UTF8")
    addressLookupClient.findByPostcode(decoded, None).map {
      case head :: tail => Ok(Json.toJson(head::tail))
      case Nil => NotFound
    }.recover {
      case e: BadRequestException =>
        Logger.warn(s"Postcode lookup service returned ${e.getMessage} for postcode $postcode", e)
        BadRequest
    }
  }

  def addressLookupById(id: String): Action[AnyContent] = CSRSecureAction(EditPersonalDetailsAndContinueRole) {
    implicit request => implicit cachedData =>
    addressLookupClient.findByAddressId(id, None).map( address => Ok(Json.toJson(address)) )
  }
}

object AddressLookupController extends AddressLookupController(
  AddressLookupClient,
  ApplicationClient
)  {
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}
