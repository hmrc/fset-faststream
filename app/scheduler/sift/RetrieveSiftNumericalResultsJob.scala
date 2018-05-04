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
import model.EmptyRequestHeader
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.NumericalTestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object RetrieveSiftNumericalResultsJob extends RetrieveSiftNumericalResultsJob {
  val numericalTestService = NumericalTestService
  val config = SiftExpiryJobConfig
}

trait RetrieveSiftNumericalResultsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val numericalTestService: NumericalTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    val intro = "Retrieving xml results for candidates in SIFT"
    Logger.info(intro)

    numericalTestService.nextTestGroupWithReportReady.flatMap {
      case Some(testGroup) =>
        Logger.info(s"$intro - processing candidate with applicationId: ${testGroup.applicationId}")

        implicit val hc = HeaderCarrier()
        implicit val rh = EmptyRequestHeader
        numericalTestService.retrieveTestResult(testGroup)
      case None =>
        Logger.info(s"$intro - found no candidates")
        Future.successful(())
    }
  }
}

object RetrieveSiftNumericalResultsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.retrieve-sift-numerical-results-job",
  name = "RetrieveSiftNumericalResultsJob"
)