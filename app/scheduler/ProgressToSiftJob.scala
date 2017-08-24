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

package scheduler

import config.WaitingScheduledJobConfig
import model.ProgressStatuses
import model.command.ApplicationForSift
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.EvaluatePhase3ResultJobConfig.conf
import services.sift.ApplicationSiftService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ProgressToSiftJob extends ProgressToSiftJob {
  val siftService = ApplicationSiftService
  val config = ProgressToSiftJobConfig
}

trait ProgressToSiftJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService

  val batchSize = conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    siftService.nextApplicationsReadyForSiftStage(batchSize).flatMap {
      case Nil => Future.successful(())
      case applications => siftService.progressApplicationToSiftStage(applications).map { result =>
        result.successes.map { application =>
          if (isSiftEnteredStatus(application)) {
            siftService.sendSiftEnteredNotification(application.applicationId).map { unit =>
              play.api.Logger.info(s"Notified ${application.applicationId} about SIFT_ENTERED")
            }
          }
        }
        play.api.Logger.info(s"Progress to sift complete - ${result.successes.size} updated and ${result.failures.size} failed to update")
      }
    }
  }

  private def isSiftEnteredStatus(application: ApplicationForSift): Boolean = {
    siftService.progressStatusForSiftStage(application) == ProgressStatuses.SIFT_ENTERED
  }

}

object ProgressToSiftJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.progress-to-sift-job",
  name = "ProgressToSiftJob"
)
