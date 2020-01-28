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

package testables

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.actions.{ SecuredRequest, UserAwareRequest }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.PersonalDetailsController
import models._
import org.joda.time.DateTime
import play.api.mvc.{ Action, AnyContent, Result }
import security.Roles.CsrAuthorization
import security.{ SecureActions, SecurityEnvironment }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.SessionKeys

// scalastyle:off method.name
trait TestableCSRSecureAction extends SecureActions {
  /**
   * Wraps the csrAction helper on a secure action.
   * If the user is not logged in then the onNotAuthenticated method on global (or controller if it overrides it) will be called.
   * The Action gets a default role that checks  if the user is active or not. \
   * If the user is inactive then the onNotAuthorized method on global will be called.
   */
  override def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_,_] => CachedData => Future[Result]): Action[AnyContent] = {
    Action.async { request =>
      val secReq = SecuredRequest[SecurityEnvironment, AnyContent](
        SecurityUser(UniqueIdentifier(UUID.randomUUID.toString).toString()),
        SessionAuthenticator(
          LoginInfo("fakeProvider", "fakeKey"),
          DateTime.now(),
          DateTime.now().plusDays(1),
          None, None
        ), request
      )
      implicit val carrier = PersonalDetailsController.hc(request)
      block(secReq)(CachedDataExample.ActiveCandidate)
    }
  }
}

trait TestableCSRUserAwareAction extends SecureActions {

  override def CSRUserAwareAction(block: UserAwareRequest[_,_] => Option[CachedData] => Future[Result]): Action[AnyContent] =
    Action.async { request =>
      val userAwareReq = UserAwareRequest[SecurityEnvironment, AnyContent](
        Some(SecurityUser("userId")),
        Some(SessionAuthenticator(
          LoginInfo("fakeProvider", "fakeKey"),
          DateTime.now(),
          DateTime.now().plusDays(1),
          None, None
        )), request)
      implicit val carrier = PersonalDetailsController.hc(request)

      val session = SessionKeys.sessionId -> s"session-${UUID.randomUUID}"

      block(userAwareReq)(Some(CachedDataExample.ActiveCandidate)).
        map(_.addingToSession(session)(request))
    }
}

trait NoIdentityTestableCSRUserAwareAction extends SecureActions {

  override def CSRUserAwareAction(block: UserAwareRequest[_,_] => Option[CachedData] => Future[Result]): Action[AnyContent] =
    Action.async { request =>
      val userAwareReq = UserAwareRequest(
        None,
        None, request)
      implicit val carrier = PersonalDetailsController.hc(request)

      val sessionId = SessionKeys.sessionId -> s"session-${UUID.randomUUID}"

      block(userAwareReq)(Some(CachedDataExample.ActiveCandidate))
        .map(_.addingToSession(sessionId)(request))
    }
}
// scalastyle:on method.name
