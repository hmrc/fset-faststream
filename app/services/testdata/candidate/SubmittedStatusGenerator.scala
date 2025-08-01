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

package services.testdata.candidate

import model.EvaluationResults.Green
import model.ProgressStatuses
import model.persisted.SchemeEvaluationResult

import javax.inject.{Inject, Singleton}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class SubmittedStatusGenerator @Inject() (val previousStatusGenerator: InProgressPreviewStatusGenerator,
                                          appRepository: GeneralApplicationRepository
                                         )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.submit(candidateInPreviousStatus.applicationId.get)
      _ = candidateInPreviousStatus.schemePreferences.map { selectedSchemes =>
        val css = selectedSchemes.schemes.map (scheme => SchemeEvaluationResult(scheme.schemeId, Green.toString))
        appRepository.updateCurrentSchemeStatus(candidateInPreviousStatus.applicationId.get, css)
      }
    } yield {
      candidateInPreviousStatus
    }
  }
}
