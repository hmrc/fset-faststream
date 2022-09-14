/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.ProgressStatuses
import model.command.ApplicationForSift
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ProgressToSiftJobImpl @Inject() (val siftService: ApplicationSiftService,
                                       val mongoComponent: MongoComponent,
                                       val config: ProgressToSiftJobConfig
                                      ) extends ProgressToSiftJob {
}

trait ProgressToSiftJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val siftService: ApplicationSiftService

  lazy val batchSize = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.info("Looking for candidates to progress to SIFT")
    siftService.nextApplicationsReadyForSiftStage(batchSize).flatMap {
      case Nil =>
        logger.info("No application found to progress to SIFT")
        Future.successful(())
      case applications => siftService.progressApplicationToSiftStage(applications).map { result =>
        result.successes.map { application =>
          if (isSiftEnteredStatus(application)) {
            siftService.saveSiftExpiryDate(application.applicationId).flatMap { expiryDate =>
              siftService.sendSiftEnteredNotification(application.applicationId, expiryDate).map(_ => ())
            }
          }
        }
        logger.info(s"Progressed to sift entered - ${result.successes.size} updated and ${result.failures.size} failed to update")
        logger.info(s"Progressed to sift entered - successful application Ids = ${result.successes.map(_.applicationId)}")
      }
    }
  }

  private def isSiftEnteredStatus(application: ApplicationForSift): Boolean = {
    siftService.progressStatusForSiftStage(application.currentSchemeStatus.map(_.schemeId)) == ProgressStatuses.SIFT_ENTERED
  }
}

@Singleton
class ProgressToSiftJobConfig @Inject()(config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.progress-to-sift-job",
  name = "ProgressToSiftJob"
)
