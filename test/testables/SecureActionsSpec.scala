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

import com.mohiva.play.silhouette.api.actions.SecuredRequest
import config.CSRCache
import controllers.BaseSpec
import models.CachedData
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{ AnyContent, Request, Result }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import security.Roles.{ CsrAuthorization, NoRole }
import security.{ SecureActions, SecurityEnvironment }

import scala.concurrent.Future
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.mvc.Results._

class SecureActionsSpec extends BaseSpec with MustMatchers {
  val request = FakeRequest(GET, "")

  "CSRSecureAction" should {
    "return an ok" in new TestFixture {
      val result = sut.CSRSecureAction(NoRole) { implicit request =>
        implicit cachedData =>
        Future.successful(Ok)
      }

      print(s"Result = $result\n")
    }

    "return an ok and cache a new record when retrieval fails due to a parsing exception" in new TestFixture {
    }
  }

  "CSRSecureAppAction" should {

  }

  "CSRUserAwareAction" should {

  }

  trait TestFixture {

    val securityEnvironment = mock[SecurityEnvironment]
    val cache = mock[CSRCache]

    val sut = new SecureActions(securityEnvironment, cache) {

      implicit def hc(implicit request: Request[_]): HeaderCarrier = HeaderCarrier()

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
