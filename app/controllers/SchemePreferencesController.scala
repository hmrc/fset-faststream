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

package controllers

import _root_.forms.SelectedSchemesForm._
import config.CSRHttp
import connectors.{ApplicationClient, SchemeClient}
import models.CachedData
import security.Roles.{PersonalDetailsRole, SchemesRole}

import scala.concurrent.Future

object SchemePreferencesController extends SchemePreferencesController(ApplicationClient) {
  val http = CSRHttp
}

abstract class SchemePreferencesController(applicationClient: ApplicationClient) extends BaseController(applicationClient) with SchemeClient{

  def present = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      getSchemePreferences(user.application.applicationId).map { selectedSchemes =>
        Ok(views.html.application.schemeSelection(selectedSchemes))
      }
  }

  def submit = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      form.bindFromRequest.fold(
        invalidForm => {
          Future.successful(Ok(views.html.application.schemeSelection(invalidForm)))
        },
        selectedSchemes => {
          for {
            _ <- updateSchemePreferences(selectedSchemes)(user.application.applicationId)
            redirect <- refreshCachedUser().map(_ => Redirect(routes.AssistanceController.present()))
          } yield {
            redirect
          }
        }
    )
  }
}
