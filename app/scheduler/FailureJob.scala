/*
 * Copyright 2023 HM Revenue & Customs
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

package scheduler

import config.WaitingScheduledJobConfig

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FsbService
import services.sift.ApplicationSiftService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SiftFailureJob @Inject() (service: ApplicationSiftService,
                                val mongoComponent: MongoComponent,
                                val config: SiftFailureJobConfig
                               ) extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.processNextApplicationFailedAtSift
  }
}

@Singleton
class SiftFailureJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.sift-failure-job",
  name = "SiftFailureJob"
)

@Singleton
class FsbOverallFailureJob @Inject() (service: FsbService,
                                      val mongoComponent: MongoComponent,
                                      val config: FsbOverallFailureJobConfig
                                     ) extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  lazy val batchSize = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    service.processApplicationsFailedAtFsb(batchSize).map { result =>
      val successfulAppIds = result.successes.map( _.applicationId )
      val failedAppIds = result.failures.map( _.applicationId )
      val msg = s"FSB failure job complete - ${result.successes.size} updated, appIds: ${successfulAppIds.mkString(",")} " +
        s"and ${result.failures.size} failed to update, appIds: ${failedAppIds.mkString(",")}"
      logger.warn(msg)
    }
  }
}

@Singleton
class FsbOverallFailureJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.fsb-overall-failure-job",
  name = "FsbOverallFailureJob"
)
