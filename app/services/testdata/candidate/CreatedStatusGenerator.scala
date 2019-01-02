/*
 * Copyright 2019 HM Revenue & Customs
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
import model.ApplicationRoute
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object CreatedStatusGenerator extends CreatedStatusGenerator {
  override val previousStatusGenerator = RegisteredStatusGenerator
  override val appRepository = applicationRepository
}

trait CreatedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository

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
