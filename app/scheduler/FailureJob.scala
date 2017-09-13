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
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FsbService
import services.sift.ApplicationSiftService

import scala.concurrent.{ ExecutionContext, Future }

object SiftFailureJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val service: ApplicationSiftService = ApplicationSiftService
  val config = SiftFailureJobConfig

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.processNextApplicationFailedAtSift
  }
}

object SiftFailureJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.sift-failure-job",
  name = "SiftFailureJob"
)

object FsbOverallFailureJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val service = FsbService
  val config = FsbOverallFailureJobConfig
  lazy val batchSize = FsbOverallFailureJobConfig.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.processApplicationsFailedAtFsb(batchSize).map { result =>
      play.api.Logger.info(s"FSB failure job complete - ${result.successes.size} updated and ${result.failures.size} failed to update")
    }
  }
}

object FsbOverallFailureJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.fsb-overall-failure-job",
  name = "FsbOverallFailureJob"
)
