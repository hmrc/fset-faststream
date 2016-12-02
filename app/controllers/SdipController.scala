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
import connectors.{ ApplicationClient, exchange }
import forms.SdipForm
import helpers.NotificationType._
import models.ApplicationRoute
import security.Roles.ActiveUserRole

import scala.concurrent.Future

object SdipController extends SdipController(ApplicationClient, CSRCache)

class SdipController(applicationClient: ApplicationClient, cacheClient: CSRCache) extends BaseController(applicationClient, cacheClient) {

  def present = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      applicationClient.findApplication(user.user.userID, exchange.FrameworkId).flatMap {
        case response if response.applicationRoute != ApplicationRoute.Faststream =>
          Future.successful(Redirect(routes.HomeController.present()))
        case response if !response.progressResponse.submitted =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.not.submitted")))
        case response if response.progressResponse.withdrawn =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.withdrew")))
        case response if response.progressResponse.phase1ProgressResponse.phase1TestsExpired =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.test.expired")))
        case response =>
          throw new Exception(response.applicationRoute)
          Future.successful(Ok(views.html.application.sdip.considerMeForSdip(SdipForm.form)))
      }
  }

  def submit = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      SdipForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.sdip.considerMeForSdip(invalidForm))),
        data =>
          applicationClient.convertToSdip(user.application.applicationId).map { _ =>
            Redirect(routes.HomeController.present()).flashing(success("faststream.becomes.sdip.success"))
          }
      )
  }
}
