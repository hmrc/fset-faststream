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

package connectors

import config.{MicroserviceAppConfig, WSHttpT}
import connectors.AuthProviderClient._
import connectors.ExchangeObjects._
import model.Exceptions.{ConnectorException, EmailTakenException}
import model.exchange.SimpleTokenResponse
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AuthProviderClient {
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

@Singleton
class AuthProviderClient @Inject() (http: WSHttpT, config: MicroserviceAppConfig) {

  val allRoles = List(AssessorRole, QacRole, FaststreamTeamRole, ServiceSupportRole, ServiceAdminRole, SuperAdminRole,
    TechnicalAdminRole)

  def getRole(roleName: String): UserRole = allRoles.find(_.name == roleName).getOrElse(
    throw new UserRoleDoesNotExistException(s"No such role: $roleName")
  )

  private lazy val ServiceName = config.authConfig.serviceName
  private val urlPrefix = "faststream"

  private lazy val url = config.userManagementConfig.url

  // TODO: only used in tdg and calls the fasttrack version in auth-provider
  def addUser(email: String, password: String, firstName: String,
              lastName: String, roles: List[UserRole])(implicit hc: HeaderCarrier): Future[UserResponse] = {
    http.POST[AddUserRequest, HttpResponse](s"$url/$urlPrefix/add",
      AddUserRequest(email.toLowerCase, password, firstName, lastName, roles.map(_.name), ServiceName)).map {
      case response if response.status == OK => response.json.as[UserResponse]
      case response if response.status == CONFLICT => throw EmailTakenException()
      case errorResponse =>
        throw new ConnectorException(s"Error response received when attempting to add new user: $firstName $lastName - $errorResponse")
    }
  }

  // TODO: looks like this is calling the fasttrack version in auth-provider. Only used in tdg
  def removeUser(userId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.DELETE[HttpResponse](s"$url/$urlPrefix/user/$userId").map { response =>
      if (response.status == NO_CONTENT) {
        ()
      } else {
        throw new ConnectorException(s"Bad response received when removing user: $userId: $response")
      }
    }
  }

  def removeAllUsers()(implicit hc: HeaderCarrier): Future[Unit] = {
    http.GET[HttpResponse](s"$url/test-commands/remove-all?service=$ServiceName").map { response =>
      if (response.status == OK) {
        ()
      } else {
        throw new ConnectorException(s"Bad response received when removing users: $response")
      }
    }
  }

  //TODO: looks like this is only used by test data generator. Investigate.
  def getToken(email: String)(implicit hc: HeaderCarrier): Future[String] = {
    http.GET[HttpResponse](s"$url/auth-code/$ServiceName/$email").map { response =>
      if (response.status == OK) {
        response.body
      } else {
        throw new ConnectorException(s"Bad response received when getting token for user: $response")
      }
    }
  }

  def activate(email: String, token: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[ActivateEmailRequest, HttpResponse](s"$url/activate",
      ActivateEmailRequest(email.toLowerCase, token, ServiceName)
    ).map {
      case response if response.status == OK => ()
      case response if response.status == GONE => throw new TokenExpiredException()
      case response if response.status == NOT_FOUND => throw new TokenEmailPairInvalidException()
      case errorResponse =>
        throw new ConnectorException(
          s"Error response ${errorResponse.status} received when calling activate - body:${errorResponse.body}"
        )
    }
  }

  def findByFirstName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    http.POST[FindByFirstNameRequest, HttpResponse](s"$url/$urlPrefix/service/$ServiceName/findByFirstName",
      FindByFirstNameRequest(roles, name)
    ).map {
      case response if response.status == OK => response.json.as[List[Candidate]]
      case response if response.status == REQUEST_ENTITY_TOO_LARGE =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(
          s"Error response ${errorResponse.status} received when calling findByFirstName - body:${errorResponse.body}"
        )
    }
  }

  def findByLastName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    http.POST[FindByLastNameRequest, HttpResponse](s"$url/$urlPrefix/service/$ServiceName/findByLastName",
      FindByLastNameRequest(roles, name)
    ).map {
      case response if response.status == OK => response.json.as[List[Candidate]]
      case response if response.status == REQUEST_ENTITY_TOO_LARGE =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(
          s"Error response ${errorResponse.status} received when calling findByLastName - body:${errorResponse.body}"
        )
    }
  }

  def findByFirstNameAndLastName(firstName: String, lastName: String, roles: List[String])
                                (implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    http.POST[FindByFirstNameLastNameRequest, HttpResponse](s"$url/$urlPrefix/service/$ServiceName/findByFirstNameLastName",
      FindByFirstNameLastNameRequest(roles, firstName, lastName)
    ).map {
      case response if response.status == OK => response.json.as[List[Candidate]]
      case response if response.status == REQUEST_ENTITY_TOO_LARGE =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(
          s"Error response ${errorResponse.status} received when calling findByFirstNameAndLastName - body:${errorResponse.body}"
        )
    }
  }

  def findByUserIds(userIds: Seq[String])(implicit hs: HeaderCarrier): Future[Seq[Candidate]] = {
    http.POST[Map[String, Seq[String]], HttpResponse](s"$url/$urlPrefix/service/$ServiceName/findUsersByIds", Map("userIds" -> userIds))
      .map { response => response.json.as[List[Candidate]] }
  }

  def findAuthInfoByUserIds(userIds: Seq[String])(implicit hs: HeaderCarrier): Future[Seq[UserAuthInfo]] = {
    http.POST[Map[String, Seq[String]], HttpResponse](s"$url/$urlPrefix/service/$ServiceName/findAuthInfoByIds", Map("userIds" -> userIds))
      .map { response => response.json.as[List[UserAuthInfo]]}
  }

  def generateAccessCode(implicit hc: HeaderCarrier): Future[SimpleTokenResponse] = {
    http.GET[SimpleTokenResponse](s"$url/user-friendly-access-token")
    .recover {
      case _ => throw new ConnectorException(s"Bad response received when getting access code")
    }
  }
}
