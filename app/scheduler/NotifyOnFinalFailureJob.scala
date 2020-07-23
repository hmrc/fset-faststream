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
import javax.inject.{ Inject, Singleton }
import play.api.{ Configuration, Logger }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FinalOutcomeService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class NotifyOnFinalFailureJobImpl @Inject() (val service: FinalOutcomeService,
                                             val mongoComponent: ReactiveMongoComponent,
                                             val config: NotifyOnFinalFailureJobConfig) extends NotifyOnFinalFailureJob {
}

trait NotifyOnFinalFailureJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val service: FinalOutcomeService

  val batchSize: Int = config.conf.batchSize.getOrElse(10)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    service.nextApplicationsFinalFailNotification(batchSize).flatMap {
      case Nil =>
        Logger.info("Progress to final failure notified complete - no candidates found")
        Future.successful(())
      case applications => service.progressApplicationsToFinalFailureNotified(applications).map { result =>
        Logger.info(
          s"Progress to final failure notified complete - ${result.successes.size} updated and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

@Singleton
class NotifyOnFinalFailureJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.notify-on-final-failure-job",
  name = "NotifyOnFinalFailureJob"
)
