/*
 * Copyright 2022 HM Revenue & Customs
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

import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.Credentials
import config.{ CSRHttp, FrontendAppConfig }
import connectors.UserManagementClient
import connectors.UserManagementClient.{ AccountLockedOutException, InvalidCredentialsException, InvalidRoleException }
import javax.inject.{ Inject, Singleton }
import models.CachedUser
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

sealed trait AccountError
case object InvalidRole extends AccountError
case object LastAttempt extends AccountError
case object AccountLocked extends AccountError
case object InvalidCredentials extends AccountError

@Singleton
class CsrCredentialsProvider @Inject() (
  config: FrontendAppConfig,
  http: CSRHttp)(override implicit val ec: ExecutionContext)
  extends UserManagementClient(config, http) with Provider {

  def authenticate(credentials: Credentials)(implicit hc: HeaderCarrier): Future[Either[AccountError, CachedUser]] = {
    signIn(credentials.identifier, credentials.password).map {
      user => Right(user.toCached)
    }.recoverWith {
      case e: InvalidCredentialsException => recordFailedAttempt(credentials.identifier)
      case e: InvalidRoleException => Future.successful(Left(InvalidRole))
    }
  }

  override def id: String = "credentials"

  def recordFailedAttempt(email: String)(implicit hc: HeaderCarrier): Future[Either[AccountError, CachedUser]] = {
    failedLogin(email).map { usr =>
      usr.lockStatus match {
        case "LOCKED" => Left(AccountLocked)
        case "LAST_ATTEMPT" => Left(LastAttempt)
        case _ => Left(InvalidCredentials)
      }
    }.recover {
      case e: InvalidCredentialsException => Left(InvalidCredentials)
      case e: AccountLockedOutException => Left(AccountLocked)
    }
  }
}
