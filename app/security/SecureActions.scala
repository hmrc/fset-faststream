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

import java.util.UUID
import com.mohiva.play.silhouette.api.actions.{ SecuredRequest, UserAwareRequest }
import com.mohiva.play.silhouette.api.{ Authorization, LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import connectors.UserManagementClient.InvalidCredentialsException
import controllers.routes
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import models.{ CachedData, CachedDataWithApp, SecurityUser, UniqueIdentifier }
import play.api.{ Logger, Logging }
import play.api.i18n.{ I18nSupport, Messages }
import play.api.mvc._
import security.Roles.CsrAuthorization
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
 *
 * The following Action wrappers exists in their respective traits:
 *
 * CSRUserAwareAction:    used when you have an action that we might have a logged in user (e.g. login page, registration page)
 * CSRSecureAction(role): used when you have an action where the user must be logged in and has and maybe has a ongoing application
 * CSRSecureAppAction(role): used when you have an action where the user must be logged in and must have an application
 *
 */

// Some of the methods in this file are intended to look like the build in Action objects, which begin with an uppercase
// so in this instance, ignore the scalastyle method rule

// scalastyle:off method.name
trait SecureActions extends I18nSupport with Logging {
  self: FrontendController =>
  implicit val ec: ExecutionContext
  val secEnv: config.SecurityEnvironment
  val silhouetteComponent: SilhouetteComponent
  val notificationTypeHelper: NotificationTypeHelper
  lazy val silhouette: Silhouette[SecurityEnvironment] = silhouetteComponent.silhouette
  import notificationTypeHelper._

  /**
   * Wraps the csrAction helper on a secure action.
   * If the user is not logged in then the onNotAuthenticated method on global (or controller if it overrides it) will be called.
   * The Action gets a default role that checks  if the user is active or not. \
   * If the user is inactive then the onNotAuthorized method on global will be called.
   */
  def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_, _] => CachedData => Future[Result]): Action[AnyContent] = {
    silhouette.SecuredAction.async { secondRequest =>
      implicit val carrier = hc(secondRequest.request)

      secEnv.userService.refreshCachedUser(UniqueIdentifier(secondRequest.identity.userID))(carrier, secondRequest).flatMap { data =>
        SecuredActionWithCSRAuthorisation(secondRequest, block, role, data, data)
      } recoverWith {
        case ice: InvalidCredentialsException => {
          logger.warn(s"Retrieved user cache data failed for userID '${secondRequest.identity.userID}. Could not recover!")
          gotoAuthentication(secondRequest)
        }
      }
    }
  }

  /**
   * CSRSecureAction that contains ApplicationData
   **/
  def CSRSecureAppAction(role: CsrAuthorization)
                        (block: SecuredRequest[_, _] => CachedDataWithApp => Future[Result]): Action[AnyContent] = {
    silhouette.SecuredAction.async { secondRequest =>
      implicit val carrier = hc(secondRequest.request)
      implicit val request = secondRequest.request

      secEnv.userService.refreshCachedUser(UniqueIdentifier(secondRequest.identity.userID))(carrier, secondRequest).flatMap {
        case CachedData(_, None) => gotoUnauthorised
        case data @ CachedData(u, Some(app)) => SecuredActionWithCSRAuthorisation(
          secondRequest,
          block, role, data, CachedDataWithApp(u, app))
      } recoverWith {
        case ice: InvalidCredentialsException => gotoAuthentication(secondRequest)
      }
    }
  }

  def CSRUserAwareAction(block: UserAwareRequest[_, _] => Option[CachedData] => Future[Result]): Action[AnyContent] =
    withSession {
      silhouette.UserAwareAction.async { request =>
        request.identity match {
          case Some(securityUser: SecurityUser) => {
            secEnv.userService.refreshCachedUser(UniqueIdentifier(securityUser.userID))(hc(request.request), request)
              .flatMap(r => block(request)(Some(r)))
          }
          case None => block(request)(None)
        }
      }
    }

  private def SecuredActionWithCSRAuthorisation[T](
                                                    originalRequest: SecuredRequest[SecurityEnvironment, AnyContent],
                                                    block: SecuredRequest[_,_] => T => Future[Result],
                                                    role: CsrAuthorization,
                                                    cachedData: CachedData,
                                                    valueForActionBlock: => T
                                                  ): Future[Result] = {

    // Create an ad hoc authorization for silhouette, to allow us to use a future to resolve the user's cached data from keystore
    val authorizer = new Authorization[SecurityUser, SessionAuthenticator] {
      override def isAuthorized[B](identity: SecurityUser,
                                   authenticator: SessionAuthenticator)(implicit request: Request[B]): Future[Boolean] =
        Future.successful(role.isAuthorized(cachedData)(originalRequest.request))
    }

    silhouette.SecuredAction(authorizer).async { securedRequest =>
      block(securedRequest)(valueForActionBlock)
    } apply originalRequest
  }

  implicit def optionUserToUser(implicit u: CachedData): Option[CachedData] = Some(u)

  implicit def userWithAppToOptionCachedUser(implicit u: CachedDataWithApp): Option[CachedData] = Some(CachedData(u.user, Some(u.application)))

  // TODO: Duplicates code from SigninService. Refactoring challenge.
  private def gotoAuthentication[_](implicit request: SecuredRequest[SecurityEnvironment, _]) = {
    secEnv.eventBus.publish(LogoutEvent(request.identity, request))
    secEnv.authenticatorService.retrieve.flatMap {
      case Some(authenticator) =>
        logger.info(s"No keystore record found for user with valid cookie (User Id = ${request.identity.userID}). " +
          s"Removing cookie and redirecting to sign in.")
        secEnv.authenticatorService.discard(authenticator, Redirect(routes.SignInController.present))
      case None => Future.successful(Redirect(routes.SignInController.present))
    }
  }

  private def gotoUnauthorised(implicit messages: Messages) = Future.successful(Redirect(routes.HomeController.present()).flashing(
    danger("access.denied")))

  /* method to wrap the functionality to generate a session is if not exists. */
  private def withSession(block: Action[AnyContent]) = Action.async { implicit request =>
    request.session.get(SessionKeys.sessionId) match {
      case Some(v) => block(request)
      case None =>
        val session = request.session + (SessionKeys.sessionId -> s"session-${UUID.randomUUID}")
        block(request).map(_.withSession(session))
    }
  }
}
