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

import connectors.ApplicationClient.{ AssistanceDetailsNotFound, PersonalDetailsNotFound }
import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.{ ApplicationClient, SchemeClient }
import helpers.NotificationType._
import security.Roles.PreviewApplicationRole

object PreviewApplicationController extends PreviewApplicationController(ApplicationClient, SchemeClient)

class PreviewApplicationController(applicationClient: ApplicationClient, schemeClient: SchemeClient) extends
  BaseController(applicationClient) {

  def present = CSRSecureAppAction(PreviewApplicationRole) { implicit request =>
    implicit user =>
      val personalDetailsFut = applicationClient.getPersonalDetails(user.user.userID, user.application.applicationId)
      val assistanceDetailsFut = applicationClient.getAssistanceDetails(user.user.userID, user.application.applicationId)
      val schemePreferencesFut = schemeClient.getSchemePreferences(user.application.applicationId)

      (for {
        gd <- personalDetailsFut
        ad <- assistanceDetailsFut
        sp <- schemePreferencesFut
      } yield {
        Ok(views.html.application.preview(gd, ad, sp, user.application))
      }).recover {
        case e @ (_: PersonalDetailsNotFound | _: AssistanceDetailsNotFound | _: SchemePreferencesNotFound) =>
          Redirect(routes.HomeController.present()).flashing(warning("info.cannot.preview.yet"))
      }
  }

  def submit = CSRSecureAppAction(PreviewApplicationRole) { implicit request =>
    implicit user =>
      applicationClient.updatePreview(user.application.applicationId).flatMap { _ =>
        updateProgress() { usr =>
            Redirect(routes.SubmitApplicationController.present())
        }
      }
  }
}
