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
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// Created to fix Scala 3 migration circular dependency issue
@Singleton
class AuthProviderClientTDG @Inject()(http: HttpClientV2, config: MicroserviceAppConfig)(implicit ec: ExecutionContext) {

  private lazy val ServiceName = config.authConfig.serviceName

  private lazy val url = config.userManagementConfig.url

  private val allRoles = List(AssessorRole, QacRole, FaststreamTeamRole, ServiceSupportRole, ServiceAdminRole, SuperAdminRole,
    TechnicalAdminRole)

  def getRole(roleName: String): UserRole = allRoles.find(_.name == roleName).getOrElse(
    throw new UserRoleDoesNotExistException(s"No such role: $roleName")
  )

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
  }

  def addUser(email: String, password: String, firstName: String,
              lastName: String, roles: List[UserRole])(implicit hc: HeaderCarrier): Future[UserResponse] = {
    import play.api.libs.ws.writeableOf_JsValue
    http.post(url"$url/faststream/add")
      .withBody(Json.toJson(AddUserRequest(email.toLowerCase, password, firstName, lastName, roles.map(_.name), ServiceName)))
      .execute[HttpResponse]
      .map {
        case response if response.status == OK => response.json.as[UserResponse]
        case response if response.status == CONFLICT => throw EmailTakenException()
        case errorResponse =>
          throw new ConnectorException(s"Error response received when attempting to add new user: $firstName $lastName - $errorResponse")
      }
  }

  def removeUser(userId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.delete(url"$url/faststream/user/$userId")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == NO_CONTENT) {
          ()
        } else {
          throw new ConnectorException(s"Bad response received when removing user: $userId: $response")
        }
      }
  }

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
  }

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
  }
}
