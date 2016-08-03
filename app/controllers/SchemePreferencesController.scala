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

import config.CSRHttp
import _root_.forms.SelectedSchemesForm
import _root_.forms.SelectedSchemesForm._

import connectors.{ ApplicationClient, SchemeClient }
import security.Roles.{PersonalDetailsRole, SchemesRole}

import scala.concurrent.Future

object SchemePreferencesController extends SchemePreferencesController(ApplicationClient) {
  val http = CSRHttp
}

abstract class SchemePreferencesController(applicationClient: ApplicationClient) extends BaseController(applicationClient) with SchemeClient{

  def present = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      /*getSchemePreferences(user.application.applicationId).flatMap { schemePreferences =>
        SelectedSchemesForm.form.fill(schemePreferences.selectedSchemes.getOrElse(EmptyData))
        Future.successful(Ok(views.html.application.schemeSelection(form)))
      }*/
      val form = SelectedSchemesForm.form.fill(EmptyData)
      Future.successful(Ok(views.html.application.schemeSelection(form)))
  }

  def submit = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      SelectedSchemesForm.form.bindFromRequest.fold(
        invalidForm => {
          Future.successful(Ok(views.html.application.schemeSelection(invalidForm)))
        },
        selectedSchemes => {
          updateSchemePreferences(selectedSchemes)(user.application.applicationId)
            .map(_ => Redirect(routes.SchemePreferencesController.present()))
        }
    )
  }
}
