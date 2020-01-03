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

package scheduler

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.ProgressToFsbOrOfferJobConfig.conf
import scheduler.clustering.SingleInstanceScheduledJob
import services.assessmentcentre.ProgressionToFsbOrOfferService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object ProgressToFsbOrOfferJob extends ProgressToFsbOrOfferJob {
  val progressionToFsbOrOfferService = ProgressionToFsbOrOfferService
  val config = ProgressToFsbOrOfferJobConfig
}

trait ProgressToFsbOrOfferJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val progressionToFsbOrOfferService: ProgressionToFsbOrOfferService

  val batchSize: Int = conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    progressionToFsbOrOfferService.nextApplicationsForFsbOrJobOffer(batchSize).flatMap {
      case Nil =>
        Logger.info("Progress to fsb or job offer complete - no candidates found")
        Future.successful(())
      case applications => progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applications).map { result =>
        Logger.info(
          s"Progress to fsb or job offer complete - ${result.successes.size} processed successfully and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

object ProgressToFsbOrOfferJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.progress-to-fsb-or-offer-job",
  name = "ProgressToFsbOrOfferJob"
)
