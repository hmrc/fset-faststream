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

package scheduler

import config.WaitingScheduledJobConfig
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
import javax.inject.Inject
import play.api.{ Configuration, Logging }
import services.assessmentcentre.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

class ProgressToAssessmentCentreJobImpl @Inject() (val assessmentCentreService: AssessmentCentreService,
                                                   val mongoComponent: MongoComponent,
                                                   val config: ProgressToAssessmentCentreJobConfig
                                                  ) extends ProgressToAssessmentCentreJob {
}

trait ProgressToAssessmentCentreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val assessmentCentreService: AssessmentCentreService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    assessmentCentreService.nextApplicationsForAssessmentCentre(batchSize).flatMap {
      case Nil =>
        logger.info("Progress to assessment centre complete - no candidates found")
        Future.successful(())
      case applications => assessmentCentreService.progressApplicationsToAssessmentCentre(applications).map { result =>
        logger.info(
          s"Progress to assessment centre complete - ${result.successes.size} updated " +
            s"appIds: ${result.successes.map(_.applicationId).mkString(",")} and ${result.failures.size} failed to update " +
            s"appIds: ${result.failures.map(_.applicationId).mkString(",")}"
        )
      }
    }
  }
}

class ProgressToAssessmentCentreJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.progress-to-assessment-centre-job",
  jobName = "ProgressToAssessmentCentreJob"
)
