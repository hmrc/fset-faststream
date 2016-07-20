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

package connectors

import config.WSHttp
import connectors.AuthProviderClient.{ ErrorRetrievingReportException, TokenEmailPairInvalidException, TokenExpiredException }
import connectors.ExchangeObjects.Implicits._
import connectors.ExchangeObjects.{ ActivateEmailRequest, AddUserRequest, Candidate, UserResponse }
import model.Exceptions.{ ConnectorException, EmailTakenException }
import play.api.http.Status._
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AuthProviderClient extends AuthProviderClient {
  sealed class ErrorRetrievingReportException(message: String) extends Exception(message)
  sealed class TokenExpiredException() extends Exception
  sealed class TokenEmailPairInvalidException() extends Exception
}

trait AuthProviderClient {
  sealed abstract class UserRole(val name: String)

  case object CandidateRole extends UserRole("candidate")

  case object ServiceManagerRole extends UserRole("service-manager")

  private val ServiceName = "faststream"

  import config.MicroserviceAppConfig.userManagementConfig._

  def candidatesReport(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    WSHttp.GET(s"$url/service/$ServiceName/findByRole/${CandidateRole.name}").map { response =>
      if (response.status == OK) {
        response.json.as[List[Candidate]]
      } else {
        throw new ErrorRetrievingReportException("There was a general problem retrieving the list of candidates")
      }
    }

  def addUser(email: String, password: String, firstName: String,
              lastName: String, role: UserRole)(implicit hc: HeaderCarrier): Future[UserResponse] =
    WSHttp.POST(s"$url/add", AddUserRequest(email.toLowerCase, password, firstName, lastName, role.name, ServiceName)).map { response =>
      response.json.as[UserResponse]
    }.recover {
      case Upstream4xxResponse(_, 409, _, _) => throw new EmailTakenException()
    }

  def removeAllUsers()(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.GET(s"$url/test-commands/remove-all").map { response =>
      if (response.status == OK) {
        ()
      } else {
        throw new ConnectorException(s"Bad response received when removing users: $response")
      }
    }

  def getToken(email: String)(implicit hc: HeaderCarrier): Future[String] =
    WSHttp.GET(s"$url/auth-code/$ServiceName/$email").map { response =>
      if (response.status == OK) {
        response.body
      } else {
        throw new ConnectorException(s"Bad response received when getting token for user: $response")
      }
    }

  def activate(email: String, token: String)(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.POST(s"$url/activate", ActivateEmailRequest(ServiceName, email.toLowerCase, token)).map(_ => (): Unit)
      .recover {
        case Upstream4xxResponse(_, 410, _, _) => throw new TokenExpiredException()
        case e: NotFoundException => throw new TokenEmailPairInvalidException()
      }
}
