/*
 * Copyright 2018 HM Revenue & Customs
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

package scheduler.sift

import config.WaitingScheduledJobConfig
import model.ProgressStatuses
import model.command.ApplicationForSift
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.sift.ProgressToSiftJobConfig.conf
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ProgressToSiftJob extends ProgressToSiftJob {
  val siftService = ApplicationSiftService
  val config = ProgressToSiftJobConfig
}

trait ProgressToSiftJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService

  lazy val batchSize = conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    Logger.info("Looking for candidates to progress to SIFT")
    siftService.nextApplicationsReadyForSiftStage(batchSize).flatMap {
      case Nil =>
        Logger.info("No application found to progress to SIFT")
        Future.successful(())
      case applications => siftService.progressApplicationToSiftStage(applications).map { result =>
        result.successes.map { application =>
          if (isSiftEnteredStatus(application)) {
            siftService.saveSiftExpiryDate(application.applicationId).flatMap { _ =>
              siftService.sendSiftEnteredNotification(application.applicationId).map(_ => ())
            }
          }
        }
        Logger.info(s"Progressed to sift entered - ${result.successes.size} updated and ${result.failures.size} failed to update")
        Logger.info(s"Progressed to sift entered - successful application Ids = ${result.successes.map(_.applicationId)}")
      }
    }
  }

  private def isSiftEnteredStatus(application: ApplicationForSift): Boolean = {
    siftService.progressStatusForSiftStage(application.currentSchemeStatus.map(_.schemeId)) == ProgressStatuses.SIFT_ENTERED
  }
}

object ProgressToSiftJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.progress-to-sift-job",
  name = "ProgressToSiftJob"
)
