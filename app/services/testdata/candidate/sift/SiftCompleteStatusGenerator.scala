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

package services.testdata.candidate.sift

import javax.inject.{Inject, Singleton}
import model.{EvaluationResults, SchemeId}
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.SchemeEvaluationResult
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import services.sift.ApplicationSiftService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SiftCompleteStatusGenerator @Inject() (val previousStatusGenerator: SiftFormsSubmittedStatusGenerator,
                                             siftService: ApplicationSiftService,
                                             appRepo: GeneralApplicationRepository,
                                             schemeRepo: SchemeRepository,
                                            )(implicit ec: ExecutionContext) extends ConstructiveGenerator with Logging {

  private def requiresSift(createCandidateData: CreateCandidateData): Boolean = {
    val schemes = createCandidateData.schemeTypes.getOrElse(List.empty[SchemeId])
    val siftableSchemes = schemeRepo.siftableSchemeIds
    val siftableAndEvaluationRequiredSchemes = schemeRepo.siftableAndEvaluationRequiredSchemeIds
    val result = siftableSchemes.exists(schemes.contains) ||
      siftableAndEvaluationRequiredSchemes.exists(schemes.contains)
    logger.warn(s"TDG - SiftCompleteStatusGenerator - should go via this generator = $result. Schemes = $schemes")
    result
  }

  private def siftSchemes(currentSchemeStats: Seq[SchemeEvaluationResult], appId: String) = Future.traverse(currentSchemeStats) { s =>
    siftService.siftApplicationForScheme(appId, s.copy(result = EvaluationResults.Green.toString))
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {
    if (requiresSift(generatorConfig)) {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
        currentSchemeStatus <- appRepo.getCurrentSchemeStatus(candidateInPreviousStatus.applicationId.get)
        _ <- siftSchemes(currentSchemeStatus, candidateInPreviousStatus.applicationId.get)
      } yield {
        candidateInPreviousStatus
      }
    } else {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      } yield {
        candidateInPreviousStatus
      }
    }
  }
}
