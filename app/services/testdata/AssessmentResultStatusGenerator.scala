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

package services.testdata

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.EvaluationResults._
import play.api.mvc.RequestHeader
import repositories._
import model.ApplicationStatus._
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import model.command.testdata.GeneratorConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreAwaitingReevalationStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val applicationStatus = ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION
  override def getAssessmentRuleCategoryResult = AssessmentRuleCategoryResult(Some(true), Some(Amber), Some(Amber), Some(Amber), Some(Amber),
    Some(Amber), None, None)
}

object AssessmentCentreFailedStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val applicationStatus = ASSESSMENT_CENTRE_FAILED
  override def getAssessmentRuleCategoryResult = AssessmentRuleCategoryResult(Some(true), Some(Red), Some(Red), Some(Red), Some(Red),
    Some(Red), None, None)
}

object AssessmentCentrePassedStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val applicationStatus = ASSESSMENT_CENTRE_PASSED
  override def getAssessmentRuleCategoryResult = AssessmentRuleCategoryResult(Some(true), Some(Green), Some(Green), Some(Green), Some(Green),
    Some(Green), None, None)
}

trait AssessmentResultStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val applicationStatus: ApplicationStatus
  def getAssessmentRuleCategoryResult: AssessmentRuleCategoryResult

  def generate(generationId: Int, generatorConfig: GeneratorConfig)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- aRepository.saveAssessmentScoreEvaluation(candidateInPreviousStatus.applicationId.get, "version1", getAssessmentRuleCategoryResult,
        applicationStatus)

    } yield {
      candidateInPreviousStatus
    }
  }
}
