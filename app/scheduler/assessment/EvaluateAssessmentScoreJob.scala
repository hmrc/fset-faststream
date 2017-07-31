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
    //scalastyle:off
    Logger.debug(s"**** START EvaluateAssessmentScoreJob - calling nextAssessmentCandidateReadyForEvaluation...")
    applicationAssessmentService.nextAssessmentCandidateReadyForEvaluation.flatMap { candidateResultsOpt =>
      Logger.debug(s"**** EvaluateAssessmentScoreJob - candidateResultsOpt = $candidateResultsOpt")
      candidateResultsOpt.map { candidateResults =>
        Logger.debug(s"**** EvaluateAssessmentScoreJob - YES FOUND DATA now calling applicationAssessmentService.evaluateAssessmentCandidate...")
        applicationAssessmentService.evaluateAssessmentCandidate(candidateResults, minimumCompetencyLevelConfig)
      }.getOrElse(Future.successful(()))
    }
  }
}

object EvaluateAssessmentScoreJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.evaluate-assessment-score-job",
  name = "EvaluateAssessmentScoreJob"
)

trait MinimumCompetencyLevelConfig {
  val minimumCompetencyLevelConfig = config.MicroserviceAppConfig.assessmentEvaluationMinimumCompetencyLevelConfig
}
