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
import connectors.ApplicationClient
import connectors.ApplicationClient.CannotSubmit
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import play.api.mvc.MessagesControllerComponents
import security.Roles.{ AbleToWithdrawApplicationRole, SubmitApplicationRole }
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SubmitApplicationController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient
)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) with CampaignAwareController {
  val appRouteConfigMap = config.applicationRoutesFrontend
  import notificationTypeHelper._

  implicit val marketingTrackingEnabled = config.marketingTrackingEnabled

  def presentSubmit = CSRSecureAppAction(SubmitApplicationRole) { implicit request =>
    implicit user =>
      if (canApplicationBeSubmitted(user.application.overriddenSubmissionDeadline)(user.application.applicationRoute)) {
        Future.successful(Ok(views.html.application.submit()))
      } else {
        Future.successful(Redirect(routes.HomeController.present()))
      }
  }

  def presentSubmitted = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.submitted(marketingTrackingEnabled)))
  }

  def submit = CSRSecureAppAction(SubmitApplicationRole) { implicit request =>
    implicit user =>
      if (canApplicationBeSubmitted(user.application.overriddenSubmissionDeadline)(user.application.applicationRoute)) {
        applicationClient.submitApplication(user.user.userID, user.application.applicationId).map { _ =>
            Redirect(routes.SubmitApplicationController.presentSubmitted)
        }.recover {
          case _: CannotSubmit => Redirect(routes.PreviewApplicationController.present).flashing(
            danger("error.cannot.submit"))
        }
      } else {
        Future.successful(Redirect(routes.HomeController.present()))
      }
  }
}
