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
import services.onlinetesting.OnlineTestService

import scala.concurrent.{ ExecutionContext, Future }

object RetrieveResultsJob extends RetrieveResultsJob {
  val onlineTestingService: OnlineTestService = OnlineTestService
}

trait RetrieveResultsJob extends SingleInstanceScheduledJob with RetrieveResultsJobConfig {
  val onlineTestingService: OnlineTestService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    Future.successful(Unit)
    //onlineTestingService.nextApplicationReadyForReportRetrieving.flatMap { reportOpt =>
    //  reportOpt.map { appWithUser =>
    //    onlineTestingService.retrieveTestResult(appWithUser, conf.waitSecs)
    //  }.getOrElse(Future.successful(()))
    //}
  }
}

trait RetrieveResultsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.retrieveResultsJobConfig
  override val configPrefix = "scheduling.online-testing.retrieve-results-job."
  override val name = "RetrieveResultsJob"
}
