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

package scheduler.onlinetesting

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.WaitingScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.{ OnlineTestRetrievePDFReportService, OnlineTestService }

import scala.concurrent.{ ExecutionContext, Future }

object RetrieveOnlineTestPDFReportJob extends RetrieveOnlineTestPDFReportJob {
  override val otRetrievePDFReportService = OnlineTestRetrievePDFReportService
  override val otService = OnlineTestService

}

trait RetrieveOnlineTestPDFReportJob extends SingleInstanceScheduledJob with RetrieveOnlineTestPDFReportJobJobConfig {
  val otRetrievePDFReportService: OnlineTestRetrievePDFReportService
  val otService: OnlineTestService

  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    otRetrievePDFReportService.nextApplicationReadyForPDFReportRetrieving.flatMap { applicationOpt =>
      applicationOpt.map { application =>
        otRetrievePDFReportService.retrievePDFReport(application, conf.waitSecs)
      }.getOrElse(Future.successful(()))
    }
  }
}

trait RetrieveOnlineTestPDFReportJobJobConfig extends BasicJobConfig[WaitingScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.retrieveOnlineTestPDFReportJobConfig
  override val configPrefix = "scheduling.online-testing.retrieve-pdf-report-job."
  override val name = "RetrieveOnlineTestPDFReportJob"
}
