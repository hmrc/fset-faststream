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

import connectors.AuthProviderClient
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

object RegisteredStatusGenerator extends RegisteredStatusGenerator {
  override val authProviderClient = AuthProviderClient
}

trait RegisteredStatusGenerator extends BaseGenerator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val authProviderClient: AuthProviderClient.type

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val firstName = generatorConfig.firstName.getOrElse(Random.getFirstname(generationId))
    val lastName = generatorConfig.lastName.getOrElse(Random.getLastname(generationId + 4000))
    val preferredName = generatorConfig.preferredName.getOrElse(s"Pref$firstName")
    val email = s"${generatorConfig.emailPrefix}${generationId + 4000}@mailinator.com"

    for {
      user <- createUser(generationId, email, firstName, lastName, preferredName, AuthProviderClient.CandidateRole)
    } yield {
      DataGenerationResponse(generationId, user.userId, None, email, firstName, lastName, preferredName)
    }
  }

  def createUser(
    generationId: Int,
    email: String,
    firstName: String, lastName: String, preferredName: String, role: AuthProviderClient.UserRole
  )(implicit hc: HeaderCarrier) = {
    for {
      user <- authProviderClient.addUser(email, "Service01", firstName, lastName, role)
      token <- authProviderClient.getToken(email)
      activateUser <- authProviderClient.activate(email, token)
    } yield {
      DataGenerationResponse(generationId, user.userId.toString, None, email, firstName, lastName, preferredName)
    }
  }

}
