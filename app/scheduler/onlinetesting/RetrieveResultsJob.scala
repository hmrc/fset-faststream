/*
 * Copyright 2016 HM Revenue & Customs
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

// TODO FIX ME!!! Once Cubiks callbacks are implemented
package scheduler.onlinetesting

import config.WaitingScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.{ OnlineTestService, Phase1TestService, Phase2TestService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object RetrievePhase1ResultsJob extends RetrieveResultsJob  with RetrievePhase1ResultsJobConfig {
  val onlineTestingService = Phase1TestService
}

object RetrievePhase2ResultsJob extends RetrieveResultsJob  with RetrievePhase2ResultsJobConfig {
  val onlineTestingService = Phase2TestService
}

trait RetrieveResultsJob extends SingleInstanceScheduledJob {
  val onlineTestingService: OnlineTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    onlineTestingService.nextTestGroupWithReportReady.flatMap {
      case Some(richTestGroup) =>
        implicit val hc = new HeaderCarrier()
        onlineTestingService.retrieveTestResult(richTestGroup)
      case None => Future.successful(())
    }
  }
}

trait RetrievePhase1ResultsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.retrievePhase1ResultsJobConfig
  override val configPrefix = "scheduling.online-testing.retrieve-phase1-results-job."
  override val name = "RetrieveResultsJob"
}

trait RetrievePhase2ResultsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.retrievePhase2ResultsJobConfig
  override val configPrefix = "scheduling.online-testing.retrieve-phase2-results-job."
  override val name = "RetrievePhase2ResultsJob"
}
