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

package services.testdata.admin

import javax.inject.{Inject, Singleton}
import connectors.AuthProviderClient
import model.exchange.testdata.CreateAdminResponse.{AssessorResponse, CreateAdminResponse}
import model.testdata.CreateAdminData.CreateAdminData
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

//object AdminCreatedStatusGenerator extends AdminCreatedStatusGenerator {
//  override val authProviderClient = AuthProviderClient
//}
@Singleton
class AdminCreatedStatusGenerator @Inject() (authProviderClient: AuthProviderClient)(
  implicit ec: ExecutionContext) extends AdminUserBaseGenerator {

  def generate(generationId: Int, createData: CreateAdminData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateAdminResponse] = {
    for {
      user <- createUser(generationId, createData)
    } yield {
      CreateAdminResponse(generationId, user.userId, None, user.email, user.firstName, user.lastName,
        roles = user.roles, disabled = user.disabled)
    }
  }

  def createUser(generationId: Int, data: CreateAdminData)
                (implicit hc: HeaderCarrier): Future[CreateAdminResponse] = {
    val roles = data.roles.map(authProviderClient.getRole)
    for {
      user <- authProviderClient.addUser(data.email, "Service01", data.firstName, data.lastName, roles)
      token <- authProviderClient.getToken(data.email)
      _ <- authProviderClient.activate(data.email, token)
    } yield {
      CreateAdminResponse(generationId, user.userId.toString, None, data.email,
        data.firstName, data.lastName, data.phone, user.roles, user.disabled, data.assessor.map(AssessorResponse(_)))
    }
  }
}
