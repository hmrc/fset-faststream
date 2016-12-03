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

import config.CSRCache
import connectors.ApplicationClient
import forms.SdipForm
import helpers.NotificationType._
import models.ConsiderMeForSdipHelper._
import security.RoleUtils._
import security.Roles.ActiveUserRole

import scala.concurrent.Future

object SdipController extends SdipController(ApplicationClient, CSRCache)

class SdipController(applicationClient: ApplicationClient, cacheClient: CSRCache) extends BaseController(applicationClient, cacheClient) {

  def present = CSRSecureAction(ActiveUserRole) { implicit request => implicit cachedData =>
    Future.successful {
      cachedData.application match {
        case Some(app) if !isFaststream(cachedData) =>
          Redirect(routes.HomeController.present()).flashing(warning("error.notfaststream"))
        case optApp if faststreamerNotEligibleForSdip(cachedData).isDefinedAt(optApp) =>
          Redirect(routes.HomeController.present(true))
        case _ => Ok(views.html.application.sdip.considerMeForSdip(SdipForm.form))
      }
    }
  }

  def submit = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit cachedData =>
      SdipForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.sdip.considerMeForSdip(invalidForm))),
        data =>
          for {
            _ <- applicationClient.convertToSdip(cachedData.application.applicationId)
            _ <- env.userService.refreshCachedUser(cachedData.user.userID)
          } yield {
            Redirect(routes.HomeController.present()).flashing(success("faststream.becomes.sdip.success"))
          }
      )
  }
}
