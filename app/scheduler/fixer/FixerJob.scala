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

package scheduler.fixer

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import play.api.Logger
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.BasicJobConfig
import services.application.ApplicationService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object FixerJob extends FixerJob {
  override val service = ApplicationService
}

trait FixerJob extends SingleInstanceScheduledJob with FixerJobConfig {
  val service: ApplicationService

  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
  implicit val rh = EmptyRequestHeader
  implicit val hc = new HeaderCarrier()
  val typesBeFixed = RequiredFixes.allFixes.map(f => FixBatch(f, jobBatchSize))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.fix(typesBeFixed)
  }
}

trait FixerJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.fixerJobConfig
  val configPrefix = "scheduling.fixer-job."
  val name = "FixerJob"
  val jobBatchSize = conf.batchSize.getOrElse(throw new IllegalArgumentException("Batch size must be defined"))
  Logger.debug(s"Max number of applications in scheduler $name: $jobBatchSize")
}
