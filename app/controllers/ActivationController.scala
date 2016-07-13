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

import _root_.forms.ActivateAccountForm
import config.CSRHttp
import connectors.ApplicationClient
import connectors.UserManagementClient.{ TokenEmailPairInvalidException, TokenExpiredException }
import helpers.NotificationType._
import security.Roles._
import security.SignInUtils

import scala.concurrent.Future

object ActivationController extends ActivationController {
  val http = CSRHttp
}

trait ActivationController extends BaseController with SignInUtils with ApplicationClient {

  val present = CSRSecureAction(NoRole) { implicit request =>
    implicit user => user.user.isActive match {
      case true => Future.successful(Redirect(routes.HomeController.present()).flashing(warning("activation.already")))
      case false => Future.successful(Ok(views.html.registration.activation(user.user.email, ActivateAccountForm.form)))
    }
  }

  val activateForm = CSRSecureAction(ActivationRole) { implicit request =>
    implicit user =>
      ActivateAccountForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.registration.activation(user.user.email, invalidForm))),
        data => {
          env.activate(user.user.email, data.activationCode).flatMap { _ =>
            signInUser(user.user.copy(isActive = true))
          }.recover {
            case e: TokenExpiredException =>
              Ok(views.html.registration.activation(
                user.user.email,
                ActivateAccountForm.form,
                notification = Some(danger("expired.activation-code"))
              ))
            case e: TokenEmailPairInvalidException =>
              Ok(views.html.registration.activation(
                user.user.email,
                ActivateAccountForm.form,
                notification = Some(danger("wrong.activation-code"))
              ))
          }
        }
      )
  }

  val resendCode = CSRSecureAction(ActivationRole) { implicit request =>
    implicit user =>
      env.resendActivationCode(user.user.email).map { _ =>
        Redirect(routes.ActivationController.present()).flashing(success("activation.code-resent"))
      }
  }

}
