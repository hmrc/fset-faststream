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

import _root_.forms.{ RequestResetPasswordForm, ResetPasswordForm, SignInForm }
import com.mohiva.play.silhouette.api.util.Credentials
import config.CSRHttp
import connectors.ApplicationClient
import connectors.UserManagementClient.{ InvalidEmailException, TokenEmailPairInvalidException, TokenExpiredException }
import helpers.NotificationType._
import models.CachedData
import security.{ InvalidRole, SignInUtils }

import scala.concurrent.Future

object PasswordResetController extends PasswordResetController {
  val http = CSRHttp
}

trait PasswordResetController extends BaseController with ApplicationClient with SignInUtils {

  def presentCode(email: Option[String]) = CSRUserAwareAction { implicit request =>
    implicit user =>
      email.filter(e => ResetPasswordForm.validateEmail(e)).map(e => sendCode(e, true)).getOrElse {
        Future.successful(Ok(views.html.registration.request_reset(RequestResetPasswordForm.form)))
      }
  }

  def submitCode = CSRUserAwareAction { implicit request =>
    implicit user =>
      RequestResetPasswordForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.registration.request_reset(invalidForm))),
        data => sendCode(data.email, isResend = false)
      )
  }

  def presentReset(email: Option[String]) = CSRUserAwareAction { implicit request =>
    implicit user =>
      email.filter(e => ResetPasswordForm.validateEmail(e)).map { e =>
        Future.successful(
          Ok(views.html.registration.reset_password(
            ResetPasswordForm.form.fill(
              ResetPasswordForm.Data(email = email.getOrElse(""), code = "", password = "", confirmpwd = "")
            )
          ))
        )
      }.getOrElse {
        Future.successful(Ok(views.html.registration.request_reset(RequestResetPasswordForm.form)))
      }
  }

  def submitReset = CSRUserAwareAction { implicit request =>
    implicit user =>
      ResetPasswordForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.registration.reset_password(invalidForm))),
        reset => resetPassword(reset.email, reset.code, reset.password)
      )
  }

  private def sendCode(email: String, isResend: Boolean)(
    implicit
    request: UserAwareRequest[_], user: Option[CachedData]
  ) = {
    env.sendResetPwdCode(email).map { _ =>
      Redirect(routes.PasswordResetController.presentReset(Some(email))).
        flashing(success(if (isResend) "resetpwd.code-resent" else "resetpwd.code-sent"), "email" -> email)
    }.recover {
      case _: InvalidEmailException =>
        Ok(views.html.registration.request_reset(
          RequestResetPasswordForm.form,
          notification = Some(danger("error.email.not.registered"))
        ))
    }
  }

  private def resetPassword(email: String, code: String, newPassword: String)(
    implicit
    request: UserAwareRequest[_], user: Option[CachedData]
  ) = {
    def renderError(error: String) = {
      Future.successful(Future.successful(Ok(views.html.registration.reset_password(
        ResetPasswordForm.form.fill(
          ResetPasswordForm.Data(email = email, code = "", password = "", confirmpwd = "")
        ),
        notification = Some(danger(error))
      ))))
    }

    env.resetPasswd(email, code, newPassword).map { _ =>
      env.credentialsProvider.authenticate(Credentials(email, newPassword)).map {
        case Right(usr) if usr.lockStatus == "LOCKED" => Future.successful(
          Redirect(routes.LockAccountController.present()).flashing("email" -> usr.email)
        )
        case Right(usr) if usr.isActive => signInUser(usr)
        case Right(usr) => signInUser(usr, redirect = Redirect(routes.ActivationController.present()))
        case Left(InvalidRole) => Future.successful(showErrorLogin(SignInForm.Data(
          signIn = email,
          signInPassword = newPassword
        ), errorMsg = "error.invalidRole"))
        case Left(_) => Future.successful(showErrorLogin(SignInForm.Data(signIn = email, signInPassword = newPassword)))
      }
    }.recover {
      case _: TokenEmailPairInvalidException =>
        renderError("error.resetpwd.code.invalid")
      case _: TokenExpiredException =>
        renderError("error.resetpwd.code.expired")
    }.flatMap(identity).flatMap(identity)
  }
}
