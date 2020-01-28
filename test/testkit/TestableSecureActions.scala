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

package testkit

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.actions.{ SecuredRequest, UserAwareRequest }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import models.SecurityUserExamples.{ ActiveCandidate, CreatedApplication }
import models.{ CachedData, CachedDataWithApp, SecurityUser }
import org.joda.time.DateTime
import play.api.mvc.{ Action, AnyContent, Request, Result }
import security.Roles.CsrAuthorization
import security.{ SecureActions, SecurityEnvironment }

import scala.concurrent.Future

trait TestableSecureActions extends SecureActions {

  def candidate: CachedData = ActiveCandidate
  def candidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CreatedApplication.copy(userId = ActiveCandidate.user.userID))

  // scalastyle:off method.name
  override def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_,_] => CachedData => Future[Result]): Action[AnyContent] =
    execute(candidate)(block)

  override def CSRSecureAppAction(role: CsrAuthorization)(block: (SecuredRequest[_,_]) => (CachedDataWithApp) =>
    Future[Result]): Action[AnyContent] = execute(candidateWithApp)(block)

  override def CSRUserAwareAction(block: UserAwareRequest[_,_] => Option[CachedData] => Future[Result]): Action[AnyContent] =
    Action.async { request =>
      val secReq = UserAwareRequest(None, None, request)
      implicit val carrier = hc(request)
      block(secReq)(None)
    }
  // scalastyle:on

  private def execute[T](result: T)(block: (SecuredRequest[_,_]) => (T) => Future[Result]): Action[AnyContent] = {
    Action.async { request =>
      val secReq = defaultAction(request)
      implicit val carrier = hc(request)
      block(secReq)(result)
    }
  }

  private def defaultAction[T](request: Request[AnyContent]) =
    SecuredRequest[SecurityEnvironment, AnyContent](
      SecurityUser(UUID.randomUUID.toString),
      SessionAuthenticator(
        LoginInfo("fakeProvider", "fakeKey"),
        DateTime.now(),
        DateTime.now().plusDays(1),
        None, None
      ),
      request
    )
}
