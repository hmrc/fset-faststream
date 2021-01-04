/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import config.{FrontendAppConfig, SecurityEnvironment}
import connectors.{ApplicationClient, UserManagementClient}
import forms.ConsiderForSdipForm
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{Inject, Singleton}
import models.ApplicationRoute._
import models.ConsiderMeForSdipHelper._
import security.RoleUtils._
import security.Roles.{ActiveUserRole, ContinueAsSdipRole}
import security.SilhouetteComponent

import scala.concurrent.{ExecutionContext, Future}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.MessagesControllerComponents

@Singleton
class ConsiderForSdipController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient,
  userManagementClient: UserManagementClient)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) with CampaignAwareController {
  import notificationTypeHelper._

  val appRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState] = config.applicationRoutesFrontend

  def present = CSRSecureAction(ActiveUserRole) { implicit request => implicit cachedData =>
    Future.successful {
      cachedData.application match {
        case Some(app) if !isFaststreamOnly(cachedData) =>
          Redirect(routes.HomeController.present())
        case Some(app) if isFaststreamOnly(cachedData) && !isNewAccountsEnabled(Sdip) =>
          Redirect(routes.HomeController.present(false))
        case optApp if faststreamerNotEligibleForSdip(cachedData).isDefinedAt(optApp) =>
          Redirect(routes.HomeController.present(true))
        case _ => Ok(views.html.application.sdip.considerMeForSdip(ConsiderForSdipForm.form))
      }
    }
  }

  def submit = CSRSecureAppAction(ActiveUserRole) { implicit request => implicit cachedData =>
      ConsiderForSdipForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.sdip.considerMeForSdip(invalidForm))),
        data =>
          for {
            _ <- applicationClient.considerForSdip(cachedData.application.applicationId)
            _ <- secEnv.userService.refreshCachedUser(cachedData.user.userID)
          } yield {
            Redirect(routes.HomeController.present()).flashing(success("faststream.becomes.sdip.success"))
          }
      )
  }

  def continueAsSdip = CSRSecureAction(ContinueAsSdipRole) { implicit request => implicit cachedData =>
      val archivedEmail = convertToArchiveEmail(cachedData.user.email)
      for {
        userToArchiveWith <- userManagementClient.register(archivedEmail, UUID.randomUUID().toString,
          cachedData.user.firstName, cachedData.user.lastName)
        _ <- applicationClient.continueAsSdip(cachedData.user.userID, userToArchiveWith.userId)
        _ <- secEnv.userService.refreshCachedUser(cachedData.user.userID)
      } yield {
        Redirect(routes.HomeController.present()).flashing(success("faststream.continueAsSdip.success"))
      }
  }
}
