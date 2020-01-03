/*
 * Copyright 2020 HM Revenue & Customs
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

package scheduler.fixer

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.ApplicationService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object FixerJob extends FixerJob {
  override val service = ApplicationService
  val config = FixerJobConfig
}

trait FixerJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  val service: ApplicationService
  lazy val jobBatchSize = config.conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  implicit val rh = EmptyRequestHeader
  implicit val hc = HeaderCarrier()
  lazy val typesBeFixed = RequiredFixes.allFixes.map(f => FixBatch(f, jobBatchSize))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.fix(typesBeFixed)
  }
}

object FixerJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.fixer-job",
  name = "FixerJob"
)
