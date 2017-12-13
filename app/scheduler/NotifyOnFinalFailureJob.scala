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
import play.api.Logger
import scheduler.ProgressToFsbOrOfferJobConfig.conf
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FinalOutcomeService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier


object NotifyOnFinalFailureJob extends NotifyOnFinalFailureJob {
  val service = FinalOutcomeService
  val config = NotifyOnFinalFailureJobConfig
}

trait NotifyOnFinalFailureJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val service: FinalOutcomeService

  val batchSize: Int = conf.batchSize.getOrElse(10)


  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    service.nextApplicationsFinalFailNotification(batchSize).flatMap {
      case Nil => Future.successful(())
      case applications => service.progressApplicationsToFinalFailureNotified(applications).map { result =>
        Logger.info(
          s"Progress to final failure notified complete - ${result.successes.size} updated and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

object NotifyOnFinalFailureJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.notify-on-final-failure-job",
  name = "NotifyOnFinalFailureJob"
)
