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

package scheduler.assessment

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.assessmentcentre.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

object EvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
  val applicationAssessmentService: AssessmentCentreService = AssessmentCentreService
}

trait EvaluateAssessmentScoreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]]
  with MinimumCompetencyLevelConfig {
  val applicationAssessmentService: AssessmentCentreService

  val config = EvaluateAssessmentScoreJobConfig

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.debug(s"EvaluateAssessmentScoreJob starting")
    applicationAssessmentService.nextAssessmentCandidatesReadyForEvaluation(config.batchSize).map { candidateResults =>
      candidateResults.map { candidateResult =>
        if (candidateResult.schemes.isEmpty) {
          Logger.debug(s"EvaluateAssessmentScoreJob - no non-RED schemes found so will not evaluate this candidate")
          Future.successful(())
        } else {
          Logger.debug(s"EvaluateAssessmentScoreJob found candidate - now evaluating...")
          applicationAssessmentService.evaluateAssessmentCandidate(candidateResult, minimumCompetencyLevelConfig)
        }
      }
    }
  }
}

object EvaluateAssessmentScoreJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.evaluate-assessment-score-job",
  name = "EvaluateAssessmentScoreJob"
) {
  val batchSize: Int = conf.batchSize.getOrElse(1)
}

trait MinimumCompetencyLevelConfig {
  val minimumCompetencyLevelConfig = config.MicroserviceAppConfig.assessmentEvaluationMinimumCompetencyLevelConfig
}
