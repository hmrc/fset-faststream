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
import javax.inject.{ Inject, Singleton }
import model.EmptyRequestHeader
import play.api.Configuration
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class FixerJobImpl @Inject() (val service: ApplicationService,
                              val mongoComponent: ReactiveMongoComponent,
                              val config: FixerJobConfig) extends FixerJob {
  //  override val service = ApplicationService
  //  val config = FixerJobConfig
}

trait FixerJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  val service: ApplicationService
  lazy val jobBatchSize = config.conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))

  implicit val rh: RequestHeader = EmptyRequestHeader
  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val typesBeFixed = RequiredFixes.allFixes.map(f => FixBatch(f, jobBatchSize))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.fix(typesBeFixed)
  }
}

@Singleton
class FixerJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.fixer-job",
  name = "FixerJob"
)
