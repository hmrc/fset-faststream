/*
 * Copyright 2019 HM Revenue & Customs
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

import _root_.forms.SignInForm
import com.mohiva.play.silhouette.api.util.Credentials
import config.CSRHttp
import connectors.ApplicationClient
import helpers.NotificationType._
import models.ApplicationRoute
import security.{ SignInService, _ }

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object SignInController extends SignInController(ApplicationClient) with SignInService {
  val http = CSRHttp
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class SignInController(val applicationClient: ApplicationClient)
  extends BaseController with SignInService {

  def present = CSRUserAwareAction { implicit request =>
    implicit user =>
      request.identity match {
        case None =>
          Future.successful(Ok(views.html.index.signin(SignInForm.form)))
        case Some(u) =>
          Future.successful(Redirect(routes.HomeController.present()))
      }
  }

  def sdipPresent = CSRUserAwareAction { implicit request =>
    implicit user =>
      request.identity match {
        case None =>
          Future.successful(Ok(views.html.index.signin(SignInForm.form.fill(SignInForm.Data("", "", Some(ApplicationRoute.SdipFaststream))))))
        case Some(u) =>
          Future.successful(Redirect(routes.HomeController.present()))
      }
  }

  // scalastyle:off cyclomatic.complexity
  def signIn = CSRUserAwareAction { implicit request =>
    implicit user =>
      SignInForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.index.signin(invalidForm))),
        data => env.credentialsProvider.authenticate(Credentials(data.signIn, data.signInPassword)).flatMap {
          case Right(usr) if usr.lockStatus == "LOCKED" => Future.successful(
            Redirect(routes.LockAccountController.present()).addingToSession("email" -> usr.email))
          case Right(usr) if usr.isActive =>
            if (data.route.contains(ApplicationRoute.SdipFaststream.toString)) {
              signInUser(usr, env, Redirect(routes.ConsiderForSdipController.present()))
            } else {
              signInUser(usr, env)
            }
          case Right(usr) => signInUser(usr, redirect = Redirect(routes.ActivationController.present()), env = env)
          case Left(InvalidRole) => Future.successful(showErrorLogin(data, errorMsg = "error.invalidRole"))
          case Left(InvalidCredentials) => Future.successful(showErrorLogin(data))
          case Left(LastAttempt) => Future.successful(showErrorLogin(data, errorMsg = "last.attempt"))
          case Left(AccountLocked) => Future.successful(Redirect(routes.LockAccountController.present())
            .addingToSession("email" -> data.signIn))
        }
      )
  }
  // scalastyle:on cyclomatic.complexity

  def signOut = CSRUserAwareAction { implicit request =>
    implicit user =>
      logOutAndRedirectUserAware(
        successAction = Redirect(routes.SignInController.present())
          .flashing(success("feedback", config.FrontendAppConfig.feedbackUrl)).withNewSession,
        failAction = Redirect(routes.SignInController.present())
          .flashing(danger("You have already signed out")).withNewSession
      )
  }
}
