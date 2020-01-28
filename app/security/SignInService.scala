/*
 * Copyright 2020 HM Revenue & Customs
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

package security

import com.mohiva.play.silhouette.api.actions.{ SecuredRequest, UserAwareRequest }
import com.mohiva.play.silhouette.api.{ LoginEvent, LoginInfo, LogoutEvent }
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import config.SecurityEnvironmentImpl
import connectors.ApplicationClient.ApplicationNotFound
import connectors.ApplicationClient
import connectors.UserManagementClient.InvalidCredentialsException
import connectors.exchange.FrameworkId
import controllers.{ BaseController, routes }
import forms.SignInForm
import forms.SignInForm.Data
import helpers.NotificationType._
import models._
import play.api.mvc.{ AnyContent, Request, RequestHeader, Result }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

trait SignInService {
  self: BaseController =>

  val applicationClient: ApplicationClient

  def signInUser(user: CachedUser,
                 env: SecurityEnvironmentImpl,
                 redirect: Result = Redirect(routes.HomeController.present())
                )(implicit request: Request[_]): Future[Result] = {
    if (user.lockStatus == "LOCKED") {
      Future.successful(Redirect(routes.LockAccountController.present()).addingToSession("email" -> user.email))
    } else {
      def signIn(app: Option[ApplicationData]) = for {
        authenticator <- env.authenticatorService.create(LoginInfo(CredentialsProvider.ID, user.userID.toString()))
        value <- env.authenticatorService.init(authenticator)
        result <- env.authenticatorService.embed(value, redirect)
      } yield {
        env.eventBus.publish(LoginEvent(SecurityUser(user.userID.toString()), request))
        result
      }

      applicationClient.findApplication(user.userID, FrameworkId).map { appData =>
        signIn(Some(appData))
      } recover {
        case _: ApplicationNotFound => signIn(None)
      } flatMap identity
    }
  }

  def showErrorLogin(data: Data, errorMsg: String = "signIn.invalid")(implicit user: Option[CachedData], request: Request[_]): Result =
    Ok(views.html.index.signin(
      SignInForm.form.fill(SignInForm.Data(signIn = data.signIn, signInPassword = "", route = data.route)), Some(danger(errorMsg))
    ))

  def logOutAndRedirectUserAware(successAction: Result, failAction: Result)(implicit request: Request[_]): Future[Result] = {
    env.authenticatorService.retrieve.map {
      case Some(authenticator) =>
        request match {
          case sr: SecuredRequest[_,_] => {
            env.eventBus.publish(LogoutEvent(sr.identity, request))
          }
          case uar: UserAwareRequest[_,_] => {
            uar.identity.foreach(identity => env.eventBus.publish(LogoutEvent(identity, request)))
          }
          case _ => ()
        }
        env.authenticatorService.discard(authenticator, successAction)
      case None => Future.successful(failAction)
    }.flatMap(identity)
  }

  def notAuthorised(request: RequestHeader): Future[Result] = {
    val sec = request.asInstanceOf[SecuredRequest[SecurityEnvironment, AnyContent]]
    env.userService.refreshCachedUser(UniqueIdentifier(sec.identity.userID))(hc(sec), sec).map {
      case cd: CachedData if cd.user.isActive => Redirect(routes.HomeController.present()).flashing(danger("access.denied"))
      case _ => Redirect(routes.ActivationController.present()).flashing(danger("access.denied"))
    } recoverWith {
      case ice: InvalidCredentialsException => {
        val signInAction = Redirect(routes.SignInController.present())
        logOutAndRedirectUserAware(signInAction, signInAction)(sec)
      }
    }
  }
}
