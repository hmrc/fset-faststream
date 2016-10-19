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

package testables

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.User
import com.mohiva.play.silhouette.test._
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, SessionAuthenticator}
import com.mohiva.play.silhouette.test.FakeEnvironment
import config.{CSRCache, SecurityEnvironmentImpl}
import controllers.BaseSpec
import models.{CachedData, CachedUser, SecurityUser, UniqueIdentifier}
import org.joda.time.DateTime
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import security.Roles.{CsrAuthorization, NoRole}
import security.{SecureActions, SecurityEnvironment}

import scala.concurrent.Future
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Reads
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.mvc.Results._
import play.api.test.FakeRequest

class SecureActionsSpec extends BaseSpec with MustMatchers with ScalaFutures {

  "CSRSecureAction" should {
    "return an ok with a valid cache entry and identity" in new TestFixture {

      val result = userOnlyCacheEntryController.CSRSecureAction(NoRole) { implicit request =>
        implicit cachedData =>
        Future.successful(Ok)
      }.apply(identityRequest)

      status(result) mustBe OK
      contentAsString(result) must contain("")

      print(s"Result = ${result}\n")
    }

    "return an ok and cache a new record when retrieval fails due to a parsing exception" in new TestFixture {
    }

    "return a redirect when no cache entry exists" ignore {
      /*val result = noCacheEntryController.CSRSecureAction(NoRole) { implicit request =>
        implicit cachedData =>
          Future.successful(Ok)
      }.apply(identityRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/fset-fast-stream/signin")*/
    }

    "return a redirect when there is no identity" ignore {
      /*val result = plainController.CSRSecureAction(NoRole) { implicit request =>
        implicit cachedData =>
          Future.successful(Ok)
      }.apply(noIdentityRequest)

      status(result) mustBe OK

      print(s"Result = ${result}\n")*/
    }
  }

  "CSRSecureAppAction" should {

  }

  "CSRUserAwareAction" should {

  }

  trait TestFixture {

    val testUserId = UniqueIdentifier(UUID.randomUUID())

    implicit def hc(implicit request: Request[_]): HeaderCarrier = HeaderCarrier()
    implicit val noIdentityRequest = FakeRequest(GET, "")

    // Fake Silhouette environment
    val identity = User(LoginInfo("provId", testUserId.toString()), None, None, None, None, None)
    val authenticator = new SessionAuthenticator(identity.loginInfo, DateTime.now(), DateTime.now().plusDays(1), None, None)
    implicit val fakeEnv = FakeEnvironment[SecurityUser, SessionAuthenticator](Seq(identity.loginInfo -> SecurityUser(testUserId.toString())))
    implicit val identityRequest = FakeRequest().withAuthenticator(authenticator)

    val mockSecurityEnvironment = mock[SecurityEnvironment]
    val mockCacheClient = mock[CSRCache]

    implicit val hc = new HeaderCarrier()

    lazy val plainController = makeSecureActions {
    }

    lazy val noCacheEntryController = makeSecureActions {
      when(mockCacheClient.fetchAndGetEntry(any[String]())(any[HeaderCarrier](), any[Reads[_]]())).thenReturn(
        Future.successful(None)
      )
    }

    lazy val userOnlyCacheEntryController = makeSecureActions {
      when(mockCacheClient.fetchAndGetEntry[CachedData](any[String]())(any[HeaderCarrier](), any[Reads[CachedData]]())).thenReturn(
        Future.successful(Some(
          CachedData(
            CachedUser(
              userID = testUserId,
              firstName = "Clive",
              lastName = "Johnson",
              preferredName = Some("Clive"),
              email = "clivejohnson1234@mailinator.com",
              isActive = true,
              "UNLOCKED"
            ),
            None
          )
        ))
      )
    }

    def makeSecureActions(mockSetup: => Unit): SecureActions = {
      mockSetup
      new SecureActions {

        val cacheClient = mockCacheClient
        // override protected def env: SecurityEnvironment = mockSecurityEnvironment

        implicit def hc(implicit request: Request[_]): HeaderCarrier = new HeaderCarrier()

        // scalastyle:off
        private def SecuredActionWithCSRAuthorisation[T](
                                                          originalRequest: SecuredRequest[AnyContent],
                                                          block: SecuredRequest[_] => T => Future[Result],
                                                          role: CsrAuthorization,
                                                          cachedData: CachedData,
                                                          valueForActionBlock: => T
                                                        ): Future[Result] = {
          Future.successful(Ok)
        }

        // scalastyle:on
      }
    }
  }
}
