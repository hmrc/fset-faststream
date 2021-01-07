/*
 * Copyright 2021 HM Revenue & Customs
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
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
//import ProgressToAssessmentCentreJobConfig.conf
import javax.inject.Inject
import play.api.{ Configuration, Logger }
import services.assessmentcentre.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

class ProgressToAssessmentCentreJobImpl @Inject() (val assessmentCentreService: AssessmentCentreService,
                                                   val mongoComponent: ReactiveMongoComponent,
                                                   val config: ProgressToAssessmentCentreJobConfig
                                                  ) extends ProgressToAssessmentCentreJob {
  //  val assessmentCentreService = AssessmentCentreService
  //  val config = ProgressToAssessmentCentreJobConfig
}

trait ProgressToAssessmentCentreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val assessmentCentreService: AssessmentCentreService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    assessmentCentreService.nextApplicationsForAssessmentCentre(batchSize).flatMap {
      case Nil =>
        Logger.info("Progress to assessment centre complete - no candidates found")
        Future.successful(())
      case applications => assessmentCentreService.progressApplicationsToAssessmentCentre(applications).map { result =>
        Logger.info(
          s"Progress to assessment centre complete - ${result.successes.size} updated and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

class ProgressToAssessmentCentreJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.progress-to-assessment-centre-job",
  name = "ProgressToAssessmentCentreJob"
)
