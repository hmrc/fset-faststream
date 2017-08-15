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

package services.testdata.candidate.sift

import model.EvaluationResults
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.SchemeEvaluationResult
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.sift.ApplicationSiftService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SiftCompleteStatusGenerator extends SiftCompleteStatusGenerator {
  val previousStatusGenerator = SiftFormsSubmittedStatusGenerator
  val siftService = ApplicationSiftService
  val appRepo = repositories.applicationRepository
}

trait SiftCompleteStatusGenerator extends ConstructiveGenerator {
  def siftService: ApplicationSiftService
  def appRepo: GeneralApplicationRepository

  private def siftSchemes(currentSchemeStats: Seq[SchemeEvaluationResult], appId: String) = Future.traverse(currentSchemeStats) { s =>
    siftService.siftApplicationForScheme(appId, s.copy(result = EvaluationResults.Green.toString))
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      currentSchemeStatus <- appRepo.getCurrentSchemeStatus(candidateInPreviousStatus.applicationId.get)
      _ <- siftSchemes(currentSchemeStatus, candidateInPreviousStatus.applicationId.get)
    } yield {
      candidateInPreviousStatus
    }

  }
}
