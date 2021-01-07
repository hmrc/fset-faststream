/*
 * Copyright 2021 HM Revenue & Customs
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

package services.testdata.candidate

import connectors.AuthProviderClient
import javax.inject.{ Inject, Singleton }
import model.SchemeId
import model.exchange.testdata.CreateAdminResponse.AssessorResponse
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.Media
import model.persisted.assessor.AssessorStatus
import model.persisted.eventschedules.SkillType
import model.testdata.CreateAdminData.AssessorData
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.MediaRepository
import services.testdata.admin.AssessorCreatedStatusGenerator
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class RegisteredStatusGenerator @Inject()(authProviderClient: AuthProviderClient,
                                          medRepository: MediaRepository,
                                          assessorGenerator: AssessorCreatedStatusGenerator,
                                          dataFaker: DataFaker) extends BaseGenerator {

  import scala.concurrent.ExecutionContext.Implicits.global

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    val firstName = generatorConfig.personalData.firstName
    val lastName = generatorConfig.personalData.lastName
    val preferredName = generatorConfig.personalData.preferredName
    val email = s"${generatorConfig.personalData.emailPrefix}@mailinator.com"
    val mediaReferrer = dataFaker.mediaReferrer

    val roles = List(AuthProviderClient.CandidateRole)
    for {
      user <- createUser(generationId, email, firstName, lastName, preferredName, roles)
      _ <- medRepository.create(Media(user.userId, mediaReferrer.getOrElse("")))

    } yield {
      CreateCandidateResponse(
        generationId, user.userId, None, None, email, firstName,
        lastName, mediaReferrer = mediaReferrer)
    }
  }

  def createUser(generationId: Int, email: String,
                 firstName: String, lastName: String,
                 preferredName: Option[String], roles: List[AuthProviderClient.UserRole])
                (implicit hc: HeaderCarrier): Future[CreateCandidateResponse] = {
    val userFuture = for {
      user <- authProviderClient.addUser(email, "Service01", firstName, lastName, roles)
      token <- authProviderClient.getToken(email)
      _ <- authProviderClient.activate(email, token)
    } yield {
      CreateCandidateResponse(generationId, user.userId.toString, None, None, email, firstName, lastName)
    }

    val assessorRoles = List(AuthProviderClient.AssessorRole, AuthProviderClient.QacRole)
    userFuture.flatMap {
      case user if assessorRoles.intersect(roles).nonEmpty =>
        assessorGenerator.createAssessor(user.userId,
          AssessorData(
            List(SkillType.ASSESSOR.toString, SkillType.QUALITY_ASSURANCE_COORDINATOR.toString, SkillType.SIFTER.toString),
            List(SchemeId("Sdip"), SchemeId("Commercial")),
            dataFaker.Random.bool,
            None,
            AssessorStatus.CREATED)).map {
          assessor => user.copy(assessor = Some(AssessorResponse.apply(assessor)))
        }
      case user => Future.successful(user)
    }
  }
}
