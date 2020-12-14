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

package controllers.reference

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import repositories.events.LocationsWithVenuesRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class LocationAndVenueController @Inject() (cc: ControllerComponents,
                                            locationsAndVenuesRepository: LocationsWithVenuesRepository) extends BackendController(cc) {

  def locationsWithVenues: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.locationsWithVenuesList.map(x => Ok(Json.toJson(x)))
  }

  def venues: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.venues.map(x => Ok(Json.toJson(x)))
  }

  def locations: Action[AnyContent] = Action.async { implicit request =>
    locationsAndVenuesRepository.locations.map(x => Ok(Json.toJson(x)))
  }
}
