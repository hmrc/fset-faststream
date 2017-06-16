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

package services.testdata.adminusers

import connectors.AuthProviderClient
import model.exchange.testdata.CreateCandidateDataGenerationResponse.CreateCandidateDataGenerationResponse
import model.exchange.testdata.{ AssessorData, AssessorResponse, CreateAdminUserDataGenerationResponse, CreateAdminUserStatusData }
import model.persisted.Media
import play.api.mvc.RequestHeader
import repositories._
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object AdminCreatedStatusGenerator extends AdminCreatedStatusGenerator {
  override val authProviderClient = AuthProviderClient
}

trait AdminCreatedStatusGenerator extends AdminUserBaseGenerator {

  import scala.concurrent.ExecutionContext.Implicits.global

  val authProviderClient: AuthProviderClient.type

  def generate(generationId: Int, createData: CreateAdminUserStatusData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateAdminUserDataGenerationResponse] = {
    for {
      user <- createUser(generationId, createData)
    } yield {
      CreateAdminUserDataGenerationResponse(generationId, user.userId, None, user.email, user.firstName, user.lastName)
    }
  }

  def createUser(generationId: Int, data: CreateAdminUserStatusData)
                (implicit hc: HeaderCarrier): Future[CreateAdminUserDataGenerationResponse] = {
    for {
      user <- authProviderClient.addUser(data.email, "Service01", data.firstName, data.lastName,
        AuthProviderClient.getRole(data.role))
      token <- authProviderClient.getToken(data.email)
      _ <- authProviderClient.activate(data.email, token)
    } yield {
      CreateAdminUserDataGenerationResponse(generationId, user.userId.toString, None, data.email,
        data.firstName, data.lastName, data.phone, data.assessor.map(AssessorResponse(_)))
    }
  }

}
