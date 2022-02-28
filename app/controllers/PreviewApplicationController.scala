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

import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.ApplicationClient.{ AssistanceDetailsNotFound, PersonalDetailsNotFound }
import connectors.SchemeClient.SchemePreferencesNotFound
import connectors.{ ApplicationClient, SchemeClient }
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.CachedDataWithApp
import play.api.mvc.MessagesControllerComponents
import security.RoleUtils._
import security.Roles.PreviewApplicationRole
import security.SilhouetteComponent

import scala.concurrent.ExecutionContext

@Singleton
class PreviewApplicationController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient,
  schemeClient: SchemeClient)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) {
  import notificationTypeHelper._

  def present = CSRSecureAppAction(PreviewApplicationRole) { implicit request =>
    implicit user =>
      val personalDetailsFut = applicationClient.getPersonalDetails(user.user.userID, user.application.applicationId)
      val schemePreferencesFut = schemeClient.getSchemePreferences(user.application.applicationId)
      val assistanceDetailsFut = applicationClient.getAssistanceDetails(user.user.userID, user.application.applicationId)

      (for {
        gd <- personalDetailsFut
        sp <- schemePreferencesFut
        ad <- assistanceDetailsFut
      } yield {
        Ok(views.html.application.preview(gd, sp, ad, user.application))
      }).recover {
        case _: PersonalDetailsNotFound | _: SchemePreferencesNotFound | _: AssistanceDetailsNotFound =>
          Redirect(routes.HomeController.present()).flashing(warning("info.cannot.preview.yet"))
      }
  }

  def submit = CSRSecureAppAction(PreviewApplicationRole) { implicit request =>
    implicit user =>
      applicationClient.updatePreview(user.application.applicationId).map { _ =>
          Redirect(routes.SubmitApplicationController.presentSubmit)
      }
  }

  def isFastStreamAndNotCivilServant(implicit user: CachedDataWithApp) =
    isFaststream(user) && !user.application.civilServiceExperienceDetails.exists(_.isCivilServant)
}
