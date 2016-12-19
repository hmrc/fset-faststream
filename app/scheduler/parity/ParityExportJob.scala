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

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.parity.ParityExportService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ParityExportJob extends ParityExportJob {
  override val service = ParityExportService
  val config = ParityExportJobConfig
}

trait ParityExportJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: ParityExportService
  def jobBatchSize = config.conf.batchSize.getOrElse(1)

  implicit val blankHeaderCarrier = HeaderCarrier()
  implicit val requestHeader = EmptyRequestHeader

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.nextApplicationsForExport(jobBatchSize).flatMap { applicationList =>
      val exportFuts = applicationList.map(applicationReadyForExport => service.exportApplication(applicationReadyForExport.applicationId))
      Future.sequence(exportFuts).map(_ => ())
    }
  }
}

object ParityExportJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.parity-export-job",
  name = "ParityExportJob"
)
