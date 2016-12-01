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
import security.Roles.{ ActiveUserRole, EditPersonalDetailsRole }

import scala.concurrent.Future

object SdipController extends SdipController(ApplicationClient, CSRCache)

class SdipController(applicationClient: ApplicationClient, cacheClient: CSRCache) extends BaseController(applicationClient, cacheClient) {

  def present = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      applicationClient.getApplicationProgress(user.application.applicationId).flatMap {
        case progress if !progress.submitted =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.not.submitted")))
        case progress if progress.withdrawn =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.withdrew")))
        case progress if progress.phase1ProgressResponse.phase1TestsExpired =>
          Future.successful(Redirect(routes.HomeController.present()).flashing(warning("error.faststream.becomes.sdip.test.expired")))
        case _ =>
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
