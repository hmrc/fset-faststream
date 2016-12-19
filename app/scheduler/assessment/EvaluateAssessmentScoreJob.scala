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

package scheduler.assessment

import config.ScheduledJobConfig
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.applicationassessment.ApplicationAssessmentService

import scala.concurrent.{ ExecutionContext, Future }

object EvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
  val applicationAssessmentService: ApplicationAssessmentService = ApplicationAssessmentService
  val config = EvaluateAssessmentScoreJobConfig
}

trait EvaluateAssessmentScoreJob extends SingleInstanceScheduledJob[EvaluateAssessmentScoreJobConfig] {
  val applicationAssessmentService: ApplicationAssessmentService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    applicationAssessmentService.nextAssessmentCandidateScoreReadyForEvaluation.flatMap { scoresOpt =>
      scoresOpt.map { scores =>
        applicationAssessmentService.evaluateAssessmentCandidateScore(scores, config.minimumCompetencyLevelConfig)
      }.getOrElse(Future.successful(()))
    }
  }
}

object EvaluateAssessmentScoreJobConfig extends EvaluateAssessmentScoreJobConfig

class EvaluateAssessmentScoreJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.evaluate-assessment-score-job",
  name = "EvaluateAssessmentScoreJob") {
  // // TODO: FSET-696 Remove this scheduler or replace the configuration to use one from assessment not phase1
  val minimumCompetencyLevelConfig = config.MicroserviceAppConfig.assessmentEvaluationMinimumCompetencyLevelConfig
}
