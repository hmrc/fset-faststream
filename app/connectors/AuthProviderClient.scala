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

package connectors

import config.{ MicroserviceAppConfig, WSHttp }
import connectors.AuthProviderClient._
import connectors.ExchangeObjects._
import model.Exceptions.{ ConnectorException, EmailTakenException }
import model.exchange.SimpleTokenResponse
import play.api.http.Status._
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException, Upstream4xxResponse }

object AuthProviderClient extends AuthProviderClient {
  sealed class ErrorRetrievingReportException(message: String) extends Exception(message)
  sealed class TokenExpiredException() extends Exception
  sealed class TokenEmailPairInvalidException() extends Exception
  sealed class UserRoleDoesNotExistException(message: String) extends Exception(message)
  sealed class TooManyResultsException(message: String) extends Exception(message)

  sealed abstract class UserRole(val name: String)

  case object CandidateRole extends UserRole("candidate")

  case object AssessorRole extends UserRole("assessor")

  case object QacRole extends UserRole("qac")

  case object FaststreamTeamRole extends UserRole("faststream-team")

  case object ServiceSupportRole extends UserRole("service-support")

  case object ServiceAdminRole extends UserRole("service-admin")

  case object SuperAdminRole extends UserRole("super-admin")

  case object TechnicalAdminRole extends UserRole("tech-admin")
}

trait AuthProviderClient {

  val allRoles = List(AssessorRole, QacRole, FaststreamTeamRole, ServiceSupportRole, ServiceAdminRole, SuperAdminRole,
    TechnicalAdminRole)

  def getRole(roleName: String): UserRole = allRoles.find(_.name == roleName).getOrElse(
    throw new UserRoleDoesNotExistException(s"No such role: $roleName")
  )

  private lazy val ServiceName = MicroserviceAppConfig.authConfig.serviceName
  private val urlPrefix = "faststream"

  import config.MicroserviceAppConfig.userManagementConfig._

  def candidatesReport(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    WSHttp.GET(s"$url/$urlPrefix/service/$ServiceName/findByRole/${CandidateRole.name}").map { response =>
      if (response.status == OK) {
        response.json.as[List[Candidate]]
      } else {
        throw new ErrorRetrievingReportException("There was a general problem retrieving the list of candidates")
      }
    }

  def addUser(email: String, password: String, firstName: String,
              lastName: String, roles: List[UserRole])(implicit hc: HeaderCarrier): Future[UserResponse] =
    WSHttp.POST(s"$url/$urlPrefix/add",
      AddUserRequest(email.toLowerCase, password, firstName, lastName, roles.map(_.name), ServiceName)).map { response =>
      response.json.as[UserResponse]
    }.recover {
      case Upstream4xxResponse(_, CONFLICT, _, _) => throw EmailTakenException()
    }


  def removeUser(userId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.DELETE(s"$url/$urlPrefix/user/$userId").map { (response) =>
      if (response.status == NO_CONTENT) {
        ()
      } else {
        throw new ConnectorException(s"Bad response received when removing user: $userId: $response")
      }
    }

  def removeAllUsers()(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.GET(s"$url/test-commands/remove-all?service=$ServiceName").map { response =>
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
    WSHttp.POST(s"$url/activate", ActivateEmailRequest(email.toLowerCase, token, ServiceName)).map(_ => (): Unit)
      .recover {
        case Upstream4xxResponse(_, GONE, _, _) => throw new TokenExpiredException()
        case _: NotFoundException => throw new TokenEmailPairInvalidException()
      }

  def findByFirstName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/$urlPrefix/service/$ServiceName/findByFirstName", FindByFirstNameRequest(roles, name)).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def findByLastName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/$urlPrefix/service/$ServiceName/findByLastName", FindByLastNameRequest(roles, name)).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def findByFirstNameAndLastName(firstName: String, lastName: String, roles: List[String])
                                (implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/$urlPrefix/service/$ServiceName/findByFirstNameLastName",
      FindByFirstNameLastNameRequest(roles, firstName, lastName)
    ).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def findByUserIds(userIds: Seq[String])(implicit hs: HeaderCarrier): Future[Seq[Candidate]] = {
    WSHttp.POST(s"$url/$urlPrefix/service/$ServiceName/findUsersByIds", Map("userIds" -> userIds)).map { (response) =>
      response.json.as[List[Candidate]]
    }
  }

  def generateAccessCode(implicit hc: HeaderCarrier): Future[SimpleTokenResponse] = {
    WSHttp.GET(s"$url/user-friendly-access-token").map { response =>
      response.json.as[SimpleTokenResponse]
    }.recover {
      case _ => throw new ConnectorException(s"Bad response received when getting access code")
    }
  }
}
