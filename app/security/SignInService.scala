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

package security

import com.mohiva.play.silhouette.api.{ LoginEvent, LoginInfo, LogoutEvent }
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import config.CSRCache
import connectors.ApplicationClient.ApplicationNotFound
import connectors.ApplicationClient
import connectors.exchange.FrameworkId
import controllers.{ BaseController, routes }
import forms.SignInForm
import forms.SignInForm.Data
import helpers.NotificationType._
import models.{ ApplicationData, CachedData, CachedUser, SecurityUser }
import play.api.i18n.Lang
import play.api.mvc.{ AnyContent, Request, RequestHeader, Result }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SignInService {
  self: BaseController =>

  val applicationClient: ApplicationClient

  def signInUser(
    user: CachedUser,
    env: SecurityEnvironment,
    redirect: Result = Redirect(routes.HomeController.present())
  )(implicit request: Request[_]): Future[Result] = {
    if (user.lockStatus == "LOCKED") {
      Future.successful(Redirect(routes.LockAccountController.present()).addingToSession("email" -> user.email))
    } else {
      def signIn(app: Option[ApplicationData]) = for {
        u <- env.userService.save(CachedData(user, app))
        authenticator <- env.authenticatorService.create(LoginInfo(CredentialsProvider.ID, user.userID.toString))
        value <- env.authenticatorService.init(authenticator)
        result <- env.authenticatorService.embed(value, Future.successful(redirect))
      } yield {
        env.eventBus.publish(LoginEvent(SecurityUser(user.userID.toString), request, request2lang))
        result
      }

      applicationClient.findApplication(user.userID, FrameworkId).map { appData =>
        signIn(Some(appData))
      } recover {
        case e: ApplicationNotFound => signIn(None)
      } flatMap identity

    }
  }

  def showErrorLogin(data: Data, errorMsg: String = "signIn.invalid")(implicit user: Option[CachedData], request: Request[_]): Result =
    Ok(views.html.index.signin(
      SignInForm.form.fill(SignInForm.Data(signIn = data.signIn, signInPassword = "")), Some(danger(errorMsg))
    ))

  def logOutAndRedirectUserAware(successAction: Future[Result], failAction: Future[Result])(implicit request: UserAwareRequest[_]) = {
    request.identity.foreach(identity => env.eventBus.publish(LogoutEvent(identity, request, request2lang)))
    env.authenticatorService.retrieve.flatMap {
      case Some(authenticator) =>
        CSRCache.remove()
        authenticator.discard(successAction)
      case None => failAction
    }
  }

  def notAuthorised(request: RequestHeader, lang: Lang): Option[Future[Result]] = {
    val sec = request.asInstanceOf[SecuredRequest[AnyContent]]
    Some(
      getCachedData(sec.identity)(hc(sec), sec).map {
        case Some(user: CachedData) if user.user.isActive => Redirect(routes.HomeController.present()).flashing(danger("access.denied"))
        case _ => Redirect(routes.ActivationController.present()).flashing(danger("access.denied"))
      }
    )
  }

}
