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

import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.api.util.{ Clock, FingerprintGenerator }
import com.mohiva.play.silhouette.api.{ Environment, EventBus, LoginInfo, Provider }
import com.mohiva.play.silhouette.impl.User
import com.mohiva.play.silhouette.test._
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.util.DefaultFingerprintGenerator
import com.mohiva.play.silhouette.test.FakeEnvironment
import config.{ CSRCache, CSRHttp, SecurityEnvironmentImpl }
import connectors.{ ApplicationClient, UserManagementClient }
import controllers.BaseSpec
import models.{ CachedData, CachedUser, SecurityUser, UniqueIdentifier }
import org.joda.time.DateTime
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{ AnyContent, Request, Result }
import play.api.test.Helpers._
import security.Roles.{ CsrAuthorization, NoRole }
import security._

import scala.concurrent.Future
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.Play
import play.api.libs.json.{ JsString, Reads }
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.cache.client.KeyStoreEntryValidationException

import language.postfixOps

class SecureActionsSpec extends BaseSpec with MustMatchers with ScalaFutures {

  "getCachedData" should {
    "return cachedData when parsing succeeds" in new TestFixture {

      val result = successfulCacheParseController.getCachedData(testSecurityUser).futureValue

      result.get mustBe an[CachedData]
    }

    "Call for cache refresh when a parsing error occurs" in new TestFixture {

      val result = unSuccessfulCacheParseController.getCachedData(testSecurityUser).futureValue

      result.get mustBe an[CachedData]
    }
  }

  trait TestFixture {

    val testUserId = UniqueIdentifier(UUID.randomUUID())
    val testSecurityUser = SecurityUser(testUserId.toString())
    val testCachedData = CachedData(
      CachedUser(
        testUserId,
        "firstName",
        "lastName",
        Some("preferredName"),
        "a@b.com",
        isActive = true,
        "UNLOCKED"
      ), None)

    implicit val request = FakeRequest(GET, "")

    implicit val hc = new HeaderCarrier()

    val mockCacheClient = mock[CSRCache]
    val mockUserCacheService = mock[UserCacheService]

    lazy val successfulCacheParseController = makeSecureActions {
      when(mockCacheClient.fetchAndGetEntry[CachedData](any())(
        any[HeaderCarrier](), any[Reads[CachedData]]())).thenReturn(Future.successful(Some(
          testCachedData
      )))
    }

    lazy val unSuccessfulCacheParseController = makeSecureActions {
      when(mockCacheClient.fetchAndGetEntry[CachedData](any())(
        any[HeaderCarrier](), any[Reads[CachedData]]())).thenReturn(Future.failed(
        new KeyStoreEntryValidationException("WantedKey", JsString("/wantedKey"), CachedData.getClass, Seq())
      ))
      when(mockUserCacheService.refreshCachedUser(any[UniqueIdentifier]())(any[HeaderCarrier](), any[Request[_]]())).thenReturn(
        Future.successful(testCachedData)
      )
    }

    def makeSecureActions(mockSetup: => Unit): SecureActions = {
      mockSetup
      new SecureActions {
        val cacheClient = mockCacheClient

        implicit def hc(implicit request: Request[_]): HeaderCarrier = new HeaderCarrier()

        override protected def env: SecurityEnvironment = new SecurityEnvironmentImpl {
           override val userService = mockUserCacheService
        }
      }
    }
  }
}
