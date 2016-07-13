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

import _root_.forms.LockAccountForm
import config.CSRHttp

import scala.concurrent.Future

object LockAccountController extends LockAccountController {
  val http = CSRHttp
}

trait LockAccountController extends BaseController {

  def present = CSRUserAwareAction { implicit request =>
    implicit user =>
      val email = request.flash.get("email")
      Future.successful(Ok(views.html.index.locked(
        LockAccountForm.form.fill(LockAccountForm.Data(email.getOrElse("")))
      )))
  }

  def submit = CSRUserAwareAction { implicit request =>
    implicit user =>
      LockAccountForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Redirect(routes.LockAccountController.present()).flashing("email" -> "")),
        data => Future.successful(Redirect(routes.PasswordResetController.presentReset(Some(data.email))).
          flashing("email" -> data.email))
      )
  }

}
