/*
 * Copyright 2017 HM Revenue & Customs
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
import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.{ ReferenceDataClient, SchemeClient }
import security.Roles.SchemesRole
import security.SilhouetteComponent

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object SchemePreferencesController extends SchemePreferencesController(SchemeClient) {
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class SchemePreferencesController(schemeClient: SchemeClient)
  extends BaseController {

  def present = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      val schemes = ReferenceDataClient.allSchemes()
      val civilServant = user.application.civilServiceExperienceDetails.exists(_.isCivilServant)
      schemeClient.getSchemePreferences(user.application.applicationId).map { selectedSchemes =>
        Ok(views.html.application.schemePreferences.schemeSelection(civilServant, form.fill(selectedSchemes)))
      }.recover {
        case e: SchemePreferencesNotFound =>
          Ok(views.html.application.schemePreferences.schemeSelection(civilServant, form))
      }
  }

  def submit = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit cachedData =>
      val isCivilServant = cachedData.application.civilServiceExperienceDetails.exists(_.isCivilServant)
      form.bindFromRequest.fold(
        invalidForm => {
          Future.successful(Ok(views.html.application.schemePreferences.schemeSelection(isCivilServant, invalidForm)))
        },
        selectedSchemes => {
          for {
            _ <- schemeClient.updateSchemePreferences(selectedSchemes)(cachedData.application.applicationId)
            redirect <- env.userService.refreshCachedUser(cachedData.user.userID).map { _ =>
                Redirect {
                  if(isCivilServant) {
                    routes.AssistanceDetailsController.present()
                  } else {
                    routes.PartnerGraduateProgrammesController.present()
                  }
                }
            }
          } yield {
            redirect
          }
        }
    )
  }
}
