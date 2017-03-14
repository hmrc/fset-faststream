/*
 * Copyright 2017 HM Revenue & Customs
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

import config.{ CSRCache, SecurityEnvironmentImpl }
import controllers.UnitSpec
import models.{ CachedData, CachedUser, SecurityUser, UniqueIdentifier }
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.libs.json.{ JsString, Reads }
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.KeyStoreEntryValidationException
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class SecureActionsSpec extends UnitSpec {

  "getCachedData" should {
    "return cachedData when parsing succeeds" in new TestFixture {
      val result = successfulCacheParseController.getCachedData(testSecurityUser).futureValue

      result.get mustBe an[CachedData]
    }

    "call for cache refresh when a parsing error occurs" in new TestFixture {
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

        override val env: SecurityEnvironmentImpl = new SecurityEnvironmentImpl {
           override val userService = mockUserCacheService
        }

        override val silhouette = SilhouetteComponent.silhouette
      }
    }
  }
}
