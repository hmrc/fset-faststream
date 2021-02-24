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
import javax.inject.{ Inject, Singleton }
import play.api.{ Configuration, Logging }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
//import scheduler.ProgressToFsbOrOfferJobConfig.conf
import services.assessmentcentre.ProgressionToFsbOrOfferService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class ProgressToFsbOrOfferJobImpl @Inject() (val progressionToFsbOrOfferService: ProgressionToFsbOrOfferService,
                                             val mongoComponent: ReactiveMongoComponent,
                                             val config: ProgressToFsbOrOfferJobConfig
                                            ) extends ProgressToFsbOrOfferJob {
}

trait ProgressToFsbOrOfferJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val progressionToFsbOrOfferService: ProgressionToFsbOrOfferService

  val batchSize: Int = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    val intro = s"Progress to fsb or job offer complete - batchSize=$batchSize"
    progressionToFsbOrOfferService.nextApplicationsForFsbOrJobOffer(batchSize).flatMap {
      case Nil =>
        logger.warn(s"$intro no candidates found")
        Future.successful(())
      case applications => progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applications).map { result =>
        logger.warn(
          s"$intro ${result.successes.size} candidate(s) processed successfully and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

@Singleton
class ProgressToFsbOrOfferJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.progress-to-fsb-or-offer-job",
  name = "ProgressToFsbOrOfferJob"
)
