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

package scheduler.assessment

import config.WaitingScheduledJobConfig

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.assessmentcentre.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EvaluateAssessmentCentreJobImpl @Inject() (val applicationAssessmentService: AssessmentCentreService,
                                                 val config: EvaluateAssessmentCentreJobConfig,
                                                 val mongoComponent: MongoComponent
                                                ) extends EvaluateAssessmentCentreJob {
}

trait EvaluateAssessmentCentreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val applicationAssessmentService: AssessmentCentreService

  val batchSize: Int = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.warn(s"EvaluateAssessmentCentreJob starting")
    applicationAssessmentService.nextAssessmentCandidatesReadyForEvaluation(batchSize).map { candidateResults =>
      candidateResults.map { candidateResult =>
        if (candidateResult.schemes.isEmpty) {
          logger.warn(s"EvaluateAssessmentCentreJob - no non-RED schemes found so will not evaluate this candidate")
          Future.successful(())
        } else {
          logger.warn(s"EvaluateAssessmentCentreJob found candidate ${candidateResult.scores.applicationId} - now evaluating...")
          applicationAssessmentService.evaluateAssessmentCandidate(candidateResult)
        }
      }
    }
  }
}

@Singleton
class EvaluateAssessmentCentreJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.evaluate-assessment-centre-job",
  name = "EvaluateAssessmentCentreJob"
)
