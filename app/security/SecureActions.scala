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

import java.util.UUID

import com.mohiva.play.silhouette.api.{ Authorization, LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import config.{ CSRCache, SecurityEnvironmentImpl }
import controllers.{ SignInController, routes }
import helpers.NotificationType._
import models.{ CachedData, CachedDataWithApp, SecurityUser, UniqueIdentifier }
import play.api.Logger
import play.api.i18n.Lang
import play.api.mvc._
import security.Roles.CsrAuthorization
import uk.gov.hmrc.http.cache.client.KeyStoreEntryValidationException
import uk.gov.hmrc.play.http.{ HeaderCarrier, SessionKeys }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import language.postfixOps
import scala.util.{ Failure, Success, Try }

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
trait SecureActions extends Silhouette[SecurityUser, SessionAuthenticator] {

  val cacheClient: CSRCache

  protected def getCachedData(securityUser: SecurityUser)(implicit hc: HeaderCarrier,
                                                              request: Request[_]): Future[Option[CachedData]] = {
    val result = cacheClient.fetchAndGetEntry[CachedData](securityUser.userID).recoverWith {
      case ex: KeyStoreEntryValidationException =>
        Logger.warn(s"Retrieved invalid cache entry for userId '${securityUser.userID}' (structure changed?). " +
          s"Attempting cache refresh from database...")
        env.userService.refreshCachedUser(UniqueIdentifier(securityUser.userID)).map(Some(_))
      case ex: Throwable =>
        Logger.warn(s"Retrieved invalid cache entry for userID '${securityUser.userID}. Could not recover!")
        throw ex
    }
    print("Returning " + result)
    result
  }

  /**
    * Wraps the csrAction helper on a secure action.
    * If the user is not logged in then the onNotAuthenticated method on global (or controller if it overrides it) will be called.
    * The Action gets a default role that checks  if the user is active or not. \
    * If the user is inactive then the onNotAuthorized method on global will be called.
    */
  def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_] => CachedData => Future[Result])
  : Action[AnyContent] = {
    Logger.debug("Starting Async")
    val result = SecuredAction.async { secondRequest =>
      implicit val carrier = hc(secondRequest.request)
      Logger.debug("Calling")
      Future.successful(InternalServerError)
      /*getCachedData(secondRequest.identity)(carrier, secondRequest).flatMap {
        case Some(data) => print("Going to SAWC")
          SecuredActionWithCSRAuthorisation(secondRequest, block, role, data, data)
        case None => print("None received!")
          gotoAuthentication(secondRequest)
      }*/
    }
    Logger.debug("Done Async")
    result
  }

  def CSRSecureAppAction(role: CsrAuthorization)
                        (block: SecuredRequest[_] => CachedDataWithApp => Future[Result]): Action[AnyContent] = {
    SecuredAction.async { secondRequest =>
      implicit val carrier = hc(secondRequest.request)
      getCachedData(secondRequest.identity)(carrier, secondRequest).flatMap {
        case Some(CachedData(_, None)) => gotoUnauthorised
        case Some(data @ CachedData(u, Some(app))) => SecuredActionWithCSRAuthorisation(secondRequest,
          block, role, data, CachedDataWithApp(u, app))
        case None => gotoAuthentication(secondRequest)
      }
    }
  }

  def CSRUserAwareAction(block: UserAwareRequest[_] => Option[CachedData] => Future[Result]): Action[AnyContent] =
    withSession {
      UserAwareAction.async { request =>
        request.identity match {
          case Some(securityUser: SecurityUser) => getCachedData(securityUser)(hc(request.request), request).flatMap(r => block(request)(r))
          case None => block(request)(None)
        }
      }
    }

  private def SecuredActionWithCSRAuthorisation[T](
                                                    originalRequest: SecuredRequest[AnyContent],
                                                    block: SecuredRequest[_] => T => Future[Result],
                                                    role: CsrAuthorization,
                                                    cachedData: CachedData,
                                                    valueForActionBlock: => T
                                                  ): Future[Result] = {

    // Create an ad hoc authorization for silhouette, to allow us to use a future to resolve the user's cached data from keystore
    val authorizer = new Authorization[SecurityUser] {
      override def isAuthorized(identity: SecurityUser)(implicit request: RequestHeader, lang: Lang) =
        role.isAuthorized(cachedData)(originalRequest.request, lang)
    }

    SecuredAction(authorizer).async { securedRequest =>
      block(securedRequest)(valueForActionBlock)
    } apply originalRequest
  }

  override protected def env: SecurityEnvironment = SecurityEnvironmentImpl

  implicit def hc(implicit request: Request[_]): HeaderCarrier

  implicit def optionUserToUser(implicit u: CachedData): Option[CachedData] = Some(u)

  implicit def userWithAppToOptionCachedUser(implicit u: CachedDataWithApp): Option[CachedData] = Some(CachedData(u.user, Some(u.application)))

  // TODO: Duplicates code from SigninService. Refactoring challenge.
  private def gotoAuthentication[_](implicit request: SecuredRequest[_]) = {
    Logger.debug("In GOTO AUTH!")
    env.eventBus.publish(LogoutEvent(request.identity, request, request2lang))
    env.authenticatorService.retrieve.flatMap {
      case Some(authenticator) =>
        Logger.info(s"No keystore record found for user with valid cookie (User Id = ${request.identity.userID}). " +
          s"Removing cookie and redirecting to sign in.")
        CSRCache.remove()
        authenticator.discard(Future.successful(Redirect(routes.SignInController.present())))
      case None => Future.successful(Redirect(routes.SignInController.present()))
    }
  }

  private def gotoUnauthorised = Future.successful(Redirect(routes.HomeController.present()).flashing(danger("access.denied")))

  /* method to wrap the functionality to generate a session is if not exists. */
  private def withSession(block: Action[AnyContent]) = Action.async { implicit request =>
    import scala.concurrent.ExecutionContext.Implicits.global

    request.session.get(SessionKeys.sessionId) match {
      case Some(v) => block(request)
      case None =>
        val session = request.session + (SessionKeys.sessionId -> s"session-${UUID.randomUUID}")
        block(request).map(_.withSession(session))
    }
  }
}
