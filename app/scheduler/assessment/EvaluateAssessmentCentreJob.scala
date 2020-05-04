/*
 * Copyright 2020 HM Revenue & Customs
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

object EvaluateAssessmentCentreJob extends EvaluateAssessmentCentreJob {
  val applicationAssessmentService: AssessmentCentreService = AssessmentCentreService
}

trait EvaluateAssessmentCentreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val applicationAssessmentService: AssessmentCentreService

  val config = EvaluateAssessmentCentreJobConfig

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.debug(s"EvaluateAssessmentCentreJob starting")
    applicationAssessmentService.nextAssessmentCandidatesReadyForEvaluation(config.batchSize).map { candidateResults =>
      candidateResults.map { candidateResult =>
        if (candidateResult.schemes.isEmpty) {
          Logger.debug(s"EvaluateAssessmentCentreJob - no non-RED schemes found so will not evaluate this candidate")
          Future.successful(())
        } else {
          Logger.debug(s"EvaluateAssessmentCentreJob found candidate ${candidateResult.scores.applicationId} - now evaluating...")
          applicationAssessmentService.evaluateAssessmentCandidate(candidateResult)
        }
      }
    }
  }
}

object EvaluateAssessmentCentreJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.evaluate-assessment-centre-job",
  name = "EvaluateAssessmentCentreJob"
) {
  val batchSize: Int = conf.batchSize.getOrElse(1)
}
