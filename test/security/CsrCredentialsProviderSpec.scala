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

import com.mohiva.play.silhouette.api.util.Credentials
import config.CSRHttp
import connectors.ExchangeObjects.UserResponse
import connectors.UserManagementClient
import connectors.UserManagementClient._
import connectors.UserManagementClient.InvalidRoleException
import controllers.{BaseControllerSpec, BaseSpec}
import models.{CachedUser, UniqueIdentifier}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class CsrCredentialsProviderSpec extends BaseSpec with ScalaFutures {

  "authenticate" should {
    "return InvalidRole if the role is invalid" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp
        override def signIn(email: String, password: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new InvalidRoleException)
        }
      }

      val result = csrCredentialsProvider.authenticate(Credentials(Id, Password))
      result.futureValue mustBe Left(InvalidRole)
    }

    "return InvalidCredentials when the credentials are invalid and we cannot record login failure" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp
        override def signIn(email: String, password: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new InvalidCredentialsException)
        }
        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new InvalidCredentialsException())
        }
      }
      val result = csrCredentialsProvider.authenticate(Credentials(Id, Password))
      result.futureValue mustBe Left(InvalidCredentials)
    }

    "return InvalidCredentials when the credentials are invalid" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp
        override def signIn(email: String, password: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new InvalidCredentialsException)
        }
        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          val userResponse = UserResponse(FirstName, LastName, Some(PreferredName), true, UserId,
            Email, "", Role, ServiceName)
          Future.successful(userResponse)
        }
      }
      val result = csrCredentialsProvider.authenticate(Credentials(Id, Password))
      result.futureValue mustBe Left(InvalidCredentials)
    }

    "return user when credentials are valid" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp
        override def signIn(email: String, password: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          val userResponse = UserResponse(FirstName, LastName, Some(PreferredName), true, UserId,
            Email, "", Role, ServiceName)
          Future.successful(userResponse)
        }

      }
      val result = csrCredentialsProvider.authenticate(Credentials(Id, Password))
      result.futureValue mustBe Right(CachedUser(UserId, FirstName, LastName, Some(PreferredName), Email, true, ""))
    }
  }
  "recordFailedAttempt" should {
    "return AccountLocked when status is locked" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp

        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          val userResponse = UserResponse("", "", Some(""), true, UniqueIdentifier(UUID.randomUUID()),
            Email, "LOCKED", "", ServiceName)
          Future.successful(userResponse)
        }
      }
      val result = csrCredentialsProvider.recordFailedAttempt(Email)
      result.futureValue mustBe Left(AccountLocked)
    }

    "return LastAttempt when status is last attempt" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp

        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          val userResponse = UserResponse("", "", Some(""), true, UniqueIdentifier(UUID.randomUUID()),
            Email, "LAST_ATTEMPT", "", ServiceName)
          Future.successful(userResponse)
        }
      }
      val result = csrCredentialsProvider.recordFailedAttempt(Email)
      result.futureValue mustBe Left(LastAttempt)
    }

    "return InvalidCredentials when status is not last attempt or locked" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp

        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          val userResponse = UserResponse("", "", Some(""), true, UniqueIdentifier(UUID.randomUUID()),
            Email, "xxxx", "", ServiceName)
          Future.successful(userResponse)
        }
      }
      val result = csrCredentialsProvider.recordFailedAttempt(Email)
      result.futureValue mustBe Left(InvalidCredentials)
    }

    "return InvalidCredentials when there is an InvalidCredentialsException" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp

        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new InvalidCredentialsException)
        }
      }
      val result = csrCredentialsProvider.recordFailedAttempt(Email)
      result.futureValue mustBe Left(InvalidCredentials)
    }

    "return AccountLocked when there is an AccountLockedOutException" in new TestFixture {
      val csrCredentialsProvider = new CsrCredentialsProvider {
        val http = CSRHttp

        override def failedLogin(email: String)(implicit hc: HeaderCarrier): Future[UserResponse] = {
          Future.failed(new AccountLockedOutException)
        }
      }
      val result = csrCredentialsProvider.recordFailedAttempt(Email)
      result.futureValue mustBe Left(AccountLocked)
    }
  }

  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val UserId = UniqueIdentifier(UUID.randomUUID())
    val FirstName = "firstName"
    val LastName = "lastName"
    val PreferredName = "preferredName"
    val Email = "email@mailinator.com"
    val Role = "role"
    val ServiceName = "faststream"
    val Id = "login"
    val Password = "password"
  }
}
