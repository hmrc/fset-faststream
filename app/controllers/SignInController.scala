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

import _root_.forms.SignInForm
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.util.Credentials
import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import helpers.NotificationType._
import security._

import scala.concurrent.Future

object SignInController extends SignInController {
  val http = CSRHttp
}

trait SignInController extends BaseController with SignInUtils with ApplicationClient {

  val present = CSRUserAwareAction { implicit request =>
    implicit user =>
      request.identity match {
        case None =>
          Future.successful(Ok(views.html.index.guestwelcome(SignInForm.form)))
        case Some(u) =>
          Future.successful(Redirect(routes.HomeController.present()))
      }
  }

  val signIn = CSRUserAwareAction { implicit request =>
    implicit user =>
      SignInForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.index.guestwelcome(invalidForm))),
        data => env.credentialsProvider.authenticate(Credentials(data.signIn, data.signInPassword)).flatMap {
          case Right(usr) if usr.lockStatus == "LOCKED" => Future.successful {
            Redirect(routes.LockAccountController.present()).flashing("email" -> usr.email)
          }
          case Right(usr) if usr.isActive => signInUser(usr)
          case Right(usr) => signInUser(usr, redirect = Redirect(routes.ActivationController.present()))
          case Left(InvalidRole) => Future.successful(showErrorLogin(data, errorMsg = "error.invalidRole"))
          case Left(InvalidCredentials) => Future.successful(showErrorLogin(data))
          case Left(LastAttempt) => Future.successful(showErrorLogin(data, errorMsg = "last.attempt"))
          case Left(AccountLocked) => Future.successful(Redirect(routes.LockAccountController.present())
            .flashing("email" -> data.signIn))
        }
      )
  }

  val signOut = CSRUserAwareAction { implicit request =>
    implicit user =>
      request.identity.map(identity => env.eventBus.publish(LogoutEvent(identity, request, request2lang)))
      env.authenticatorService.retrieve.flatMap {
        case Some(authenticator) =>
          CSRCache.remove()
          authenticator.discard(Future.successful(Redirect(routes.SignInController.present()).
            flashing(success("feedback", config.FrontendAppConfig.feedbackUrl)).withNewSession))
        case None => Future.successful(Redirect(routes.SignInController.present()).
          flashing(danger("You have already signed out")).withNewSession)
      }
  }

}
