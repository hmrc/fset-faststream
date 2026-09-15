/*
 * Copyright 2026 HM Revenue & Customs
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

class AcceptFsacAssessorScoresJobImpl @Inject() (val assessmentCentreService: AssessmentCentreService,
                                                   val mongoComponent: MongoComponent,
                                                   val config: ProgressToAssessmentCentreJobConfig
                                                  ) extends AcceptFsacAssessorScoresJob {
}

trait AcceptFsacAssessorScoresJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val assessmentCentreService: AssessmentCentreService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    assessmentCentreService.findAssessedCandidates(batchSize).flatMap {
      case Nil =>
        logger.info("Accept FSAC assessor scores job complete - no candidates found")
        Future.successful(())
      case applications => assessmentCentreService.approveAssessedCandidates(applications).map { result =>
        logger.info(
          s"Accept FSAC assessor scores job complete - ${result.successes.size} updated " +
            s"appIds: ${result.successes.map(_.applicationId).mkString(",")} and ${result.failures.size} failed to update " +
            s"appIds: ${result.failures.map(_.applicationId).mkString(",")}"
        )
      }
    }
  }
}

class AcceptFsacAssessorScoresJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.accept-fsac-assessor-scores-job",
  jobName = "AcceptFsacAssessorScoresJob"
)
