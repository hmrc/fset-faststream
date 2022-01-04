/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.Exceptions.{ CannotUpdateSchemePreferences, SchemePreferencesNotFound }
import model.SelectedSchemes
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import services.scheme.SchemePreferencesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SchemePreferencesController @Inject() (cc: ControllerComponents,
                                             schemePreferencesService: SchemePreferencesService) extends BackendController(cc) {

  def find(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    schemePreferencesService.find(applicationId) map { sp =>
      Ok(Json.toJson(sp))
    } recover {
      case _: SchemePreferencesNotFound => NotFound(s"Cannot find scheme preferences for applicationId: $applicationId")
    }
  }

  def update(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SelectedSchemes] { schemePref =>
      schemePreferencesService.update(applicationId, schemePref) map { _ =>
        Ok
      } recover {
        case _: CannotUpdateSchemePreferences => BadRequest(s"Cannot update scheme preferences for applicationId: $applicationId")
      }
    }
  }
}
