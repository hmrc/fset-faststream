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

package scheduler.parity

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import model.ApplicationStatus.{ ApplicationStatus, READY_FOR_EXPORT, READY_TO_UPDATE }
import play.api.Logger
import play.api.mvc.RequestHeader
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.parity.ParityExportService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ParityExportJob extends ParityExportJob {
  override val service = ParityExportService
  val config = ParityExportJobConfig
}

object ParityUpdateExportJob extends ParityUpdateExportJob {
  override val service = ParityExportService
  val config = ParityUpdateExportJobConfig
}

trait ParityExportJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: ParityExportService
  def jobBatchSize = config.conf.batchSize.getOrElse(1)

  implicit val blankHeaderCarrier = HeaderCarrier()
  implicit val requestHeader = EmptyRequestHeader

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.warn("=== Starting export scheduler")
    service.nextApplicationsForExport(jobBatchSize, READY_FOR_EXPORT).flatMap { applicationList =>
      Logger.warn("=== Applications to export: " + applicationList.map(_.applicationId))
      val exportFuts = applicationList.map(applicationReadyForExport => service.exportApplication(applicationReadyForExport.applicationId))
      val res = Future.sequence(exportFuts).map(_ => ())
      Logger.warn("=== Done")
      res
    }
  }
}

trait ParityUpdateExportJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: ParityExportService
  def jobBatchSize = config.conf.batchSize.getOrElse(1)

  implicit val blankHeaderCarrier = HeaderCarrier()
  implicit val requestHeader = EmptyRequestHeader

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.nextApplicationsForExport(jobBatchSize, READY_TO_UPDATE).flatMap { applicationList =>
      val exportFuts = applicationList.map(applicationReadyForExport => service.updateExportApplication(applicationReadyForExport.applicationId))
      Future.sequence(exportFuts).map(_ => ())
    }
  }
}

object ParityExportJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.parity-export-job",
  name = "ParityExportJob"
)

object ParityUpdateExportJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.parity-update-export-job",
  name = "ParityUpdateExportJob"
)
