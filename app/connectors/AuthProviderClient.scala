/*
 * Copyright 2023 HM Revenue & Customs
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

import config.MicroserviceAppConfig
import connectors.AuthProviderClient.*
import connectors.ExchangeObjects.*
import model.Exceptions.{ConnectorException, EmailTakenException}
import model.exchange.SimpleTokenResponse
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
class AuthProviderClient @Inject() (http: HttpClientV2, config: MicroserviceAppConfig)(
  implicit ec: ExecutionContext) {

  private val allRoles = List(AssessorRole, QacRole, FaststreamTeamRole, ServiceSupportRole, ServiceAdminRole, SuperAdminRole,
    TechnicalAdminRole)

  //AuthProviderClientSpec
  def getRole(roleName: String): UserRole = allRoles.find(_.name == roleName).getOrElse(
    throw new UserRoleDoesNotExistException(s"No such role: $roleName")
  )

  private lazy val ServiceName = config.authConfig.serviceName
  private val urlPrefix = "faststream"

  private lazy val url = config.userManagementConfig.url

/*
  // TODO: only used in tdg and calls the fasttrack version in auth-provider
  def addUser(email: String, password: String, firstName: String,
              lastName: String, roles: List[UserRole])(implicit hc: HeaderCarrier): Future[UserResponse] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/add")
      .withBody(Json.toJson(AddUserRequest(email.toLowerCase, password, firstName, lastName, roles.map(_.name), ServiceName)))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => response.json.as[UserResponse]
        case response if response.status == CONFLICT => throw EmailTakenException()
        case errorResponse =>
          throw new ConnectorException(s"Error response received when attempting to add new user: $firstName $lastName - $errorResponse")
      }
  }*/
/*
  // TODO: looks like this is calling the fasttrack version in auth-provider. Only used in tdg
  def removeUser(userId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.delete(url"$url/$urlPrefix/user/$userId")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == NO_CONTENT) {
          ()
        } else {
          throw new ConnectorException(s"Bad response received when removing user: $userId: $response")
        }
      }
  }*/
/*
  def removeAllUsers()(implicit hc: HeaderCarrier): Future[Unit] = {
    http.get(url"$url/test-only/test-commands/remove-all?service=$ServiceName")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == OK) {
          ()
        } else {
          throw new ConnectorException(s"Bad response received when removing users: $response")
        }
     }
  }*/
/*
  // This is only used by test data generator. It is called and we create user accounts and admin accounts
  def getToken(email: String)(implicit hc: HeaderCarrier): Future[String] = {
    http.get(url"$url/test-only/auth-code/$ServiceName/$email")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == OK) {
          response.body
        } else {
          throw new ConnectorException(s"Bad response received when getting token for user: $response")
        }
     }
  }*/
/*
  def activate(email: String, token: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/activate")
      .withBody(Json.toJson(ActivateEmailRequest(email.toLowerCase, token, ServiceName)))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => ()
        case response if response.status == GONE => throw new TokenExpiredException()
        case response if response.status == NOT_FOUND => throw new TokenEmailPairInvalidException()
        case errorResponse =>
          throw new ConnectorException(
            s"Error response ${errorResponse.status} received when calling activate - body:${errorResponse.body}"
          )
      }
  }*/

  def findByFirstName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findByFirstName")
      .withBody(Json.toJson(FindByFirstNameRequest(roles, name)))
      .execute[HttpResponse]
      .map {
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
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findByLastName")
      .withBody(Json.toJson(FindByLastNameRequest(roles, name)))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => response.json.as[List[Candidate]]
        case response if response.status == REQUEST_ENTITY_TOO_LARGE =>
          throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
        case errorResponse =>
          throw new ConnectorException(
            s"Error response ${errorResponse.status} received when calling findByLastName - body:${errorResponse.body}"
          )
      }
  }
/*
  def findByFirstNameAndLastName(firstName: String, lastName: String, roles: List[String])
                                (implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findByFirstNameLastName")
      .withBody(Json.toJson(FindByFirstNameLastNameRequest(roles, firstName, lastName)))
      .execute[Either[UpstreamErrorResponse, List[Candidate]]]
      .map {
        case Right(response) => response
        case Left(tooLargeEx) if tooLargeEx.statusCode == REQUEST_ENTITY_TOO_LARGE =>
          throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
        case Left(ex) =>
          throw new ConnectorException(
            s"Error response ${ex.statusCode} received when calling findByFirstNameAndLastName - message:${ex.getMessage}"
          )
      }
  }
 */

  def findByFirstNameAndLastName(firstName: String, lastName: String, roles: List[String])
                                (implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findByFirstNameLastName")
      .withBody(Json.toJson(FindByFirstNameLastNameRequest(roles, firstName, lastName)))
      .execute[HttpResponse]
      .map {
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
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findUsersByIds")
      .withBody(Json.toJson(Map("userIds" -> userIds)))
      .execute[HttpResponse]
      .map ( response => response.json.as[List[Candidate]] )
  }

  def findAuthInfoByUserIds(userIds: Seq[String])(implicit hs: HeaderCarrier): Future[Seq[UserAuthInfo]] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/$urlPrefix/service/$ServiceName/findAuthInfoByIds")
      .withBody(Json.toJson(Map("userIds" -> userIds)))
      .execute[HttpResponse]
      .map ( response => response.json.as[List[UserAuthInfo]] )
  }

  def generateAccessCode(implicit hc: HeaderCarrier): Future[SimpleTokenResponse] = {
    http.get(url"$url/user-friendly-access-token")
      .execute[SimpleTokenResponse]
      .recover {
        case _ => throw new ConnectorException(s"Bad response received when getting access code")
      }
  }
}
