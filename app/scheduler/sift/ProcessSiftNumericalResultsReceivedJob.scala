/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestService

import scala.concurrent.{ ExecutionContext, Future }

object ProcessSiftNumericalResultsReceivedJob extends ProcessSiftNumericalResultsReceivedJob {
  val numericalTestService = NumericalTestService
  val config = ProcessSiftNumericalResultsReceivedJobConfig
}

trait ProcessSiftNumericalResultsReceivedJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val numericalTestService: NumericalTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    val intro = "Processing candidates in SIFT who have received numerical test results"
    Logger.info(intro)

    numericalTestService.nextApplicationWithResultsReceived.flatMap {
      case Some(applicationId) =>
        Logger.info(s"$intro - processing candidate with applicationId: $applicationId")
        numericalTestService.progressToSiftReady(applicationId)
      case None =>
        Logger.info(s"$intro - found no candidates")
        Future.successful(())
    }
  }
}

object ProcessSiftNumericalResultsReceivedJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.process-sift-numerical-results-received-job",
  name = "ProcessSiftNumericalResultsReceivedJob"
)
