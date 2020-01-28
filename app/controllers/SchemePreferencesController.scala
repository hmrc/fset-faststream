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

import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.exchange.referencedata.SchemeId
import connectors.{ ReferenceDataClient, SchemeClient }
import forms.SelectedSchemesForm
import models.ApplicationRoute
import models.page.SelectedSchemesPage
import security.Roles.SchemesRole
import security.SilhouetteComponent

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object SchemePreferencesController extends SchemePreferencesController(SchemeClient, ReferenceDataClient) {
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class SchemePreferencesController(
  schemeClient: SchemeClient,
  referenceDataClient: ReferenceDataClient
) extends BaseController {


  def present = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit cachedData =>
      referenceDataClient.allSchemes().flatMap { schemes =>
        val page = SelectedSchemesPage(schemes)
        val formObj = new SelectedSchemesForm(schemes, cachedData.application.isSdipFaststream)
        val civilServant = cachedData.application.civilServiceExperienceDetails.exists(_.isCivilServant)
        schemeClient.getSchemePreferences(cachedData.application.applicationId).map { selectedSchemes =>
          Ok(views.html.application.schemePreferences.schemeSelection(page, civilServant, formObj.form.fill(selectedSchemes)))
        }.recover {
          case e: SchemePreferencesNotFound =>
            Ok(views.html.application.schemePreferences.schemeSelection(page, civilServant, formObj.form))
        }
      }
  }

  def submit = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit cachedData =>
      referenceDataClient.allSchemes().flatMap { schemes =>
        val isCivilServant = cachedData.application.civilServiceExperienceDetails.exists(_.isCivilServant)
        new SelectedSchemesForm(schemes, cachedData.application.isSdipFaststream).form.bindFromRequest.fold(
          invalidForm => {
            val page = SelectedSchemesPage(schemes)
            Future.successful(Ok(views.html.application.schemePreferences.schemeSelection(page, isCivilServant, invalidForm)))
          },
          selectedSchemes => {
            val sdip = SchemeId("Sdip")
            val selectedSchemesAmended = cachedData.application.applicationRoute match {
              case ApplicationRoute.SdipFaststream if !selectedSchemes.schemes.contains(sdip.value) => {
                selectedSchemes.copy(schemes = selectedSchemes.schemes :+ sdip.value)
              }
              case _ => selectedSchemes
            }

            for {
              _ <- schemeClient.updateSchemePreferences(selectedSchemesAmended)(cachedData.application.applicationId)
              redirect <- env.userService.refreshCachedUser(cachedData.user.userID).map { _ =>
                Redirect(routes.AssistanceDetailsController.present())
              }
            } yield {
              redirect
            }
          }
        )
      }
  }
}
