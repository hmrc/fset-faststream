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
import model.persisted.Media
import play.api.mvc.RequestHeader
import repositories._
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier
import model.command.testdata.GeneratorConfig

object RegisteredStatusGenerator extends RegisteredStatusGenerator {
  override val authProviderClient = AuthProviderClient
  override val medRepository = mediaRepository

}

trait RegisteredStatusGenerator extends BaseGenerator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val authProviderClient: AuthProviderClient.type
  val medRepository: MediaRepository


  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val firstName = generatorConfig.personalData.flatMap(_.firstName).getOrElse(Random.getFirstname(generationId))
    val lastName = generatorConfig.personalData.flatMap(_.lastName).getOrElse(Random.getLastname(generationId))
    val preferredName = generatorConfig.personalData.flatMap(_.preferredName).getOrElse(s"Pref$firstName")
    val email = s"${generatorConfig.personalData.flatMap(_.emailPrefix).getOrElse("tesf" + Random.number())}-${generationId}@mailinator.com"
    val mediaReferrer = Random.mediaReferrer

    for {
      user <- createUser(generationId, email, firstName, lastName, preferredName, AuthProviderClient.CandidateRole)
      _ <- medRepository.create(Media(user.userId, mediaReferrer.getOrElse("")))
    } yield {
      DataGenerationResponse(generationId, user.userId, None, email, firstName, lastName, preferredName, mediaReferrer = mediaReferrer)
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
