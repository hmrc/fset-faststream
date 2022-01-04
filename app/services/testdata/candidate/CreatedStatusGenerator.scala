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

package services.testdata.candidate

import connectors.ExchangeObjects
import javax.inject.{ Inject, Singleton }
import model.ApplicationRoute
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CreatedStatusGenerator @Inject() (val previousStatusGenerator: RegisteredStatusGenerator,
                                        appRepository: GeneralApplicationRepository) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      applicationId <- createApplication(candidateInPreviousStatus.userId, generatorConfig.statusData.applicationRoute)
    } yield {
      candidateInPreviousStatus.copy(
        applicationId = Some(applicationId)
      )
    }
  }

  private def createApplication(userId: String, route: ApplicationRoute.ApplicationRoute): Future[String] = {
    appRepository.create(userId, ExchangeObjects.frameworkId, route).map { application =>
      application.applicationId
    }
  }
}
