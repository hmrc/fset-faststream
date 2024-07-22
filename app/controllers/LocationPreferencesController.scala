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

package controllers

import model.Exceptions.{CannotUpdateLocationPreferences, LocationPreferencesNotFound}
import model.SelectedLocations
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.location.LocationPreferencesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LocationPreferencesController @Inject()(cc: ControllerComponents,
                                              locationPreferencesService: LocationPreferencesService) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def find(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationPreferencesService.find(applicationId) map { locations =>
      Ok(Json.toJson(locations))
    } recover {
      case _: LocationPreferencesNotFound => NotFound(s"Cannot find location preferences for applicationId: $applicationId")
    }
  }

  def update(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SelectedLocations] { locations =>
      locationPreferencesService.update(applicationId, locations) map { _ =>
        Ok
      } recover {
        case _: CannotUpdateLocationPreferences => BadRequest(s"Cannot update location preferences for applicationId: $applicationId")
      }
    }
  }
}
