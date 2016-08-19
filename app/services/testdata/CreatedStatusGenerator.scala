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

package services.testdata

import connectors.{AuthProviderClient, ExchangeObjects}
import model.PersistedObjects.{PersistedAnswer, PersistedQuestion}
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CreatedStatusGenerator extends CreatedStatusGenerator {
  override val previousStatusGenerator = RegisteredStatusGenerator
  override val appRepository = applicationRepository
}

trait CreatedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      applicationId <- createApplication(candidateInPreviousStatus.userId)
    } yield {
      candidateInPreviousStatus.copy(
        applicationId = Some(applicationId)
      )
    }
  }

  def createUser(
    email: String,
    firstName: String, lastName: String, role: AuthProviderClient.UserRole
  )(implicit hc: HeaderCarrier): Future[String] = {
    for {
      user <- AuthProviderClient.addUser(email, "Service01", firstName, lastName, role)
      token <- AuthProviderClient.getToken(email)
      activateUser <- AuthProviderClient.activate(email, token)
    } yield {
      user.userId.toString
    }
  }

  private def createApplication(userId: String): Future[String] = {
    appRepository.create(userId, ExchangeObjects.frameworkId).map { application =>
      application.applicationId
    }
  }
}
