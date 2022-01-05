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

import _root_.forms.{ RequestResetPasswordForm, ResetPasswordForm, SignInForm }
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.api.util.Credentials
import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.UserManagementClient.{ InvalidEmailException, TokenEmailPairInvalidException, TokenExpiredException }
import connectors.{ ApplicationClient, UserManagementClient }
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.CachedData
import play.api.mvc.MessagesControllerComponents
import security.{ InvalidRole, SignInService, SilhouetteComponent }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class PasswordResetController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val applicationClient: ApplicationClient,
  val notificationTypeHelper: NotificationTypeHelper,
  userManagementClient: UserManagementClient,
  signInService: SignInService,
  formWrapper: ResetPasswordForm)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) {
  import notificationTypeHelper._

  def presentCode() = CSRUserAwareAction { implicit request =>
    implicit user =>
      val email = request.session.get("email")
      email.filter(e => formWrapper.validateEmail(e)).map(e => sendCode(e, isResend = true)).getOrElse {
        Future.successful {
          Ok(views.html.registration.request_reset(RequestResetPasswordForm.form))
        }
      }
  }

  def submitCode() = CSRUserAwareAction { implicit request =>
    implicit user =>
      RequestResetPasswordForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.registration.request_reset(invalidForm))),
        data => sendCode(data.email, isResend = false)
      )
  }

  def presentReset() = CSRUserAwareAction { implicit request =>
    implicit user =>
      val email = request.session.get("email")
      email.filter(e => formWrapper.validateEmail(e)).map { e =>
        Future.successful(
          Ok(views.html.registration.reset_password(
            formWrapper.form.fill(
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
      formWrapper.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.registration.reset_password(invalidForm))),
        reset => resetPassword(reset.email, reset.code, reset.password)
      )
  }

  private def sendCode(email: String, isResend: Boolean)
                      (implicit request: UserAwareRequest[_,_], user: Option[CachedData]) = {
    userManagementClient.sendResetPwdCode(email).map { _ =>
      Redirect(routes.PasswordResetController.presentReset())
        .flashing(info(if (isResend) "resetpwd.code-resent" else "resetpwd.code-sent"))
        .addingToSession("email" -> email)
    }.recover {
      case _: InvalidEmailException =>
        Ok(views.html.registration.request_reset(
          RequestResetPasswordForm.form,
          notification = Some(danger("error.email.not.registered"))
        ))
    }
  }

  private def resetPassword(email: String, code: String, newPassword: String)
                           (implicit request: UserAwareRequest[_,_], user: Option[CachedData]) = {
    def renderError(error: String) = {
      Future.successful(Future.successful(Ok(views.html.registration.reset_password(
        formWrapper.form.fill(
          ResetPasswordForm.Data(email = email, code = "", password = "", confirmpwd = "")
        ),
        notification = Some(danger(error))
      ))))
    }

    userManagementClient.resetPasswd(email, code, newPassword).map { _ =>
      secEnv.credentialsProvider.authenticate(Credentials(email, newPassword)).map {
        case Right(usr) if usr.lockStatus == "LOCKED" => Future.successful(Redirect(routes.LockAccountController.present()))
        case Right(usr) if usr.isActive => signInService.signInUser(usr).map(_.removingFromSession("email"))
        case Right(usr) => signInService.signInUser(usr, redirect = Redirect(routes.ActivationController.present()))
          .map(_.removingFromSession("email"))
        case Left(InvalidRole) => Future.successful(signInService.showErrorLogin(SignInForm.Data(
          signIn = email,
          signInPassword = newPassword
        ), errorMsg = "error.invalidRole"))
        case Left(_) => Future.successful(signInService.showErrorLogin(SignInForm.Data(signIn = email, signInPassword = newPassword)))
      }
    }.recover {
      case _: TokenEmailPairInvalidException =>
        renderError("error.resetpwd.code.invalid")
      case _: TokenExpiredException =>
        renderError("error.resetpwd.code.expired")
    }.flatMap(identity).flatMap(identity)
  }
}
