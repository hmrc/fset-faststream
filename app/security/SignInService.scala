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

package security

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.api.{LoginEvent, LoginInfo, LogoutEvent}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import config.FrontendAppConfig
import connectors.ApplicationClient
import connectors.ApplicationClient.ApplicationNotFound
import connectors.UserManagementClient.InvalidCredentialsException
import connectors.exchange.FrameworkId
import play.api.mvc.Results.Redirect
import controllers.routes
import forms.SignInForm
import forms.SignInForm.Data
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models._
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SignInService @Inject() (
  config: FrontendAppConfig,
  secEnv: _root_.config.SecurityEnvironment,
  applicationClient: ApplicationClient,
  notificationTypeHelper: NotificationTypeHelper,
  formWrapper: SignInForm)(implicit val ec: ExecutionContext) {
  import notificationTypeHelper._

  def signInUser(user: CachedUser,
    redirect: Result = Redirect(routes.HomeController.present())
  )(implicit request: Request[_], hc: HeaderCarrier): Future[Result] = {
    if (user.lockStatus == "LOCKED") {
      Future.successful(Redirect(routes.LockAccountController.present).addingToSession("email" -> user.email))
    } else {
      def signIn(app: Option[ApplicationData]) = for {
        authenticator <- secEnv.authenticatorService.create(LoginInfo(CredentialsProvider.ID, user.userID.toString()))
        value <- secEnv.authenticatorService.init(authenticator)
        result <- secEnv.authenticatorService.embed(value, redirect)
      } yield {
        secEnv.eventBus.publish(LoginEvent(SecurityUser(user.userID.toString()), request))
        result
      }

      applicationClient.findApplication(user.userID, FrameworkId).map { appData =>
        signIn(Some(appData))
      } recover {
        case _: ApplicationNotFound => signIn(None)
      } flatMap identity
    }
  }

  def showErrorLogin(data: Data, errorMsg: String = "signIn.invalid")(
    implicit user: Option[CachedData], request: Request[_], flash: Flash, messages: Messages): Result = {
    implicit val feedBackUrl = config.feedbackUrl
    implicit val trackingConsentConfig = config.trackingConsentConfig
    play.api.mvc.Results.Ok(views.html.index.signin(
      formWrapper.form.fill(SignInForm.Data(signIn = data.signIn, signInPassword = "", route = data.route)),
      Some(danger(errorMsg))
    ))
  }

  def logOutAndRedirectUserAware(successAction: Result, failAction: Result)(implicit request: Request[_]): Future[Result] = {
    secEnv.authenticatorService.retrieve.map {
      case Some(authenticator) =>
        request match {
          case sr: SecuredRequest[_, _] => {
            secEnv.eventBus.publish(LogoutEvent(sr.identity, request))
          }
          case uar: UserAwareRequest[_, _] => {
            uar.identity.foreach(identity => secEnv.eventBus.publish(LogoutEvent(identity, request)))
          }
          case _ => ()
        }
        secEnv.authenticatorService.discard(authenticator, successAction)
      case None => Future.successful(failAction)
    }.flatMap(identity)
  }

  def notAuthorised(request: RequestHeader, hc: HeaderCarrier)(implicit messages: Messages): Future[Result] = {
    val sec = request.asInstanceOf[SecuredRequest[SecurityEnvironment, AnyContent]]
    secEnv.userService.refreshCachedUser(UniqueIdentifier(sec.identity.userID))(hc, sec).map {
      case cd: CachedData if cd.user.isActive => Redirect(routes.HomeController.present()).flashing(
        danger("access.denied"))
      case _ => Redirect(routes.ActivationController.present).flashing(danger("access.denied"))
    } recoverWith {
      case ice: InvalidCredentialsException => {
        val signInAction = Redirect(routes.SignInController.present)
        logOutAndRedirectUserAware(signInAction, signInAction)(sec)
      }
    }
  }
}
