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
import services.onlinetesting.OnlineTestService
import services.sift.ApplicationSiftService$
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ProgressToAssessmentCentreJob extends ProgressToAssessmentCentreJob {
  val siftService = ApplicationSiftService$
  val config = SiftConfig
}

trait ProgressToAssessmentCentreJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService$

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    siftService.nextTestGroupWithReportReady.flatMap {
      case Some(richTestGroup) =>
        implicit val hc = new HeaderCarrier()
        siftService.retrieveTestResult(richTestGroup)
      case None => Future.successful(())
    }
  }
}

object SiftConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.progress-to-sift-job",
  name = "ProgressToSiftJob"
)
