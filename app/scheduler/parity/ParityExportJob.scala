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

package scheduler.parity

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.{ MicroserviceAppConfig, ScheduledJobConfig }
import play.api.Logger
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.BasicJobConfig
import services.application.ApplicationService
import services.parity.ParityExportService

import scala.concurrent.{ ExecutionContext, Future }

object ParityExportJob extends ParityExportJob {
  override val service = ParityExportService
  override val parityExportJobConfig = MicroserviceAppConfig.parityExportJobConfig
}

trait ParityExportJob extends SingleInstanceScheduledJob with ParityExportJobConfig {
  val service: ParityExportService
  val parityExportJobConfig: ScheduledJobConfig

  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.nextApplicationsForExport(parityExportJobConfig.batchSize.getOrElse(1)).flatMap { applicationList =>
      val exportFuts = applicationList.map(applicationReadyForExport => service.exportApplication(applicationReadyForExport.applicationId))
      Future.sequence(exportFuts).map(_ => ())
    }
  }
}

trait ParityExportJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.parityExportJobConfig
  val configPrefix = "scheduling.parity-export-job."
  val name = "ParityExportJob"
  val jobBatchSize = conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))
  Logger.debug(s"Max number of applications in scheduler $name: $jobBatchSize")
}
