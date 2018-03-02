/*
 * Copyright 2018 HM Revenue & Customs
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

import config.SecurityEnvironmentImpl
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.Play.current
import play.api.test.FakeRequest
import play.api.test.Helpers._
import security.Roles.NoRole
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

class SecureActionsSpec extends UnitWithAppSpec {

  "getCachedData" should {
    "always refresh cached user data" ignore new TestFixture {
      alwaysRefreshCacheParseController.CSRSecureAction(NoRole) {
        _ => _ => Future.successful(Ok(""))
      }.apply(request).futureValue

      alwaysRefreshCacheParseController.CSRSecureAppAction(NoRole) {
        _ => _ => Future.successful(Ok(""))
      }.apply(request).futureValue

      alwaysRefreshCacheParseController.CSRUserAwareAction {
        _ => _ => Future.successful(Ok(""))
      }.apply(request).futureValue

      verify(mockUserCacheService, times(3)).refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]])
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

    val mockUserCacheService = mock[UserCacheService]

    lazy val alwaysRefreshCacheParseController = makeSecureActions {
      when(mockUserCacheService.refreshCachedUser(any[UniqueIdentifier]())(any[HeaderCarrier](), any[Request[_]]())).thenReturn(
        Future.successful(testCachedData)
      )
    }

    def makeSecureActions(mockSetup: => Unit): SecureActions = {
      mockSetup
      new SecureActions {
        implicit def hc(implicit request: Request[_]): HeaderCarrier = new HeaderCarrier()

        override val env: SecurityEnvironmentImpl = new SecurityEnvironmentImpl {
           override val userService = mockUserCacheService
        }

        override lazy val silhouette = SilhouetteComponent.silhouette
      }
    }
  }
}
