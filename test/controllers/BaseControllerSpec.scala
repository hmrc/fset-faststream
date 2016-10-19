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

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import config.CSRCache
import models.SecurityUserExamples._
import models._
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import security.Roles.CsrAuthorization
import security.{ SecureActions, SecurityEnvironment, SignInService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

/**
  * Each Controller test needs to extend this class to simplify controller testing
  */
abstract class BaseControllerSpec extends BaseSpec with ScalaFutures {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val rh: RequestHeader = FakeRequest()
  val securityEnvironment = mock[security.SecurityEnvironment]

  def currentCandidate: CachedData = ActiveCandidate

  def currentUserId = currentCandidate.user.userID

  def currentEmail = currentCandidate.user.email

  def currentUser = currentCandidate.user

  def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CreatedApplication.copy(userId = ActiveCandidate.user.userID))

  def currentApplicationId = currentCandidateWithApp.application.applicationId

  def randomUUID = UniqueIdentifier(UUID.randomUUID().toString)

  def fakeRequest = FakeRequest().withSession(CSRF.TokenName -> CSRF.SignedTokenProvider.generateToken)

  /**
    * Wrapper on SignInService class to allow mocking
    *
    * @see security.SignInService
    */
  trait TestableSignInService extends SignInService {
    self: BaseController =>

    val signInService: SignInService

    override def signInUser(user: CachedUser,
                            env: SecurityEnvironment,
                            redirect: Result = Redirect(routes.HomeController.present())
                           )(implicit request: Request[_]): Future[Result] =
      signInService.signInUser(user, env, redirect)(request)
  }

  def assertPageTitle(result: Future[Result], expectedTitle: String) = {
    status(result) must be(OK)
    val content = contentAsString(result)
    content must include(s"<title>$expectedTitle")
  }

  def assertPageRedirection(result: Future[Result], expectedUrl: String) = {
    status(result) must be(SEE_OTHER)
    redirectLocation(result) must be(Some(expectedUrl))
  }

  val mockSecurityEnv = mock[SecurityEnvironment]
  val mockCSRCache = mock[CSRCache]

  // scalastyle:off method.name
  abstr TestableSecureActions extends SecureActions(mockSecurityEnv, mockCSRCache) {

    val Candidate: CachedData = currentCandidate
    val CandidateWithApp: CachedDataWithApp = currentCandidateWithApp

    override def CSRSecureAction(role: CsrAuthorization)(block: SecuredRequest[_] => CachedData => Future[Result]): Action[AnyContent] =
      execute(Candidate)(block)

    override def CSRSecureAppAction(role: CsrAuthorization)(block: (SecuredRequest[_]) => (CachedDataWithApp) =>
      Future[Result]): Action[AnyContent] = execute(CandidateWithApp)(block)

    override def CSRUserAwareAction(block: UserAwareRequest[_] => Option[CachedData] => Future[Result]): Action[AnyContent] =
      Action.async { request =>
        val secReq = UserAwareRequest(None, None, request)
        implicit val carrier = hc(request)
        block(secReq)(None)
      }

    private def execute[T](result: T)(block: (SecuredRequest[_]) => (T) => Future[Result]): Action[AnyContent] = {
      Action.async { request =>
        val secReq = defaultAction(request)
        implicit val carrier = hc(request)
        block(secReq)(result)
      }
    }

    private def defaultAction[T](request: Request[AnyContent]) =
      SecuredRequest(
        SecurityUser(UUID.randomUUID.toString),
        SessionAuthenticator(
          LoginInfo("fakeProvider", "fakeKey"),
          DateTime.now(),
          DateTime.now().plusDays(1),
          None, None
        ), request
      )

  }
}

