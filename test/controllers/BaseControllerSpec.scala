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

import models.{CachedUser, UniqueIdentifier}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.mvc.{Request, RequestHeader, Result}
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import security.{SecurityEnvironment, SignInService}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

/**
  * Each Controller test needs to extend this class to simplify controller testing
  */
abstract class BaseControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures with OneServerPerSuite {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val rh: RequestHeader = FakeRequest()

  def randomUUID = UniqueIdentifier(UUID.randomUUID().toString)

  def fakeRequest = FakeRequest().withSession(CSRF.TokenName -> CSRF.SignedTokenProvider.generateToken)

  trait TestableSignInService extends SignInService {
    self: BaseController =>

    val signInService: SignInService

    override def signInUser(user: CachedUser,
                            env: SecurityEnvironment,
                            redirect: Result = Redirect(routes.HomeController.present())
                           )(implicit request: Request[_]): Future[Result] =
      signInService.signInUser(user, env, redirect)(request)
  }
}
