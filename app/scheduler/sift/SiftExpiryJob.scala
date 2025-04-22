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

package scheduler.sift

import config.{MicroserviceAppConfig, WaitingScheduledJobConfig}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import javax.inject.{ Inject, Singleton }
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SiftExpiryJobImpl @Inject() (val siftService: ApplicationSiftService,
                                   val mongoComponent: MongoComponent,
                                   val config: SiftExpiryJobConfig,
                                   val appConfig: MicroserviceAppConfig
                                  ) extends SiftExpiryJob {
  override val gracePeriodInSecs = appConfig.onlineTestsGatewayConfig.numericalTests.gracePeriodInSecs
}

trait SiftExpiryJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] with Logging {
  val siftService: ApplicationSiftService
  val gracePeriodInSecs: Int
  lazy val batchSize = config.conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.info(s"Expiring candidates in SIFT with batchSize = $batchSize, gracePeriodInSecs = $gracePeriodInSecs")
    siftService.processExpiredCandidates(batchSize, gracePeriodInSecs).map(_ => ())
  }
}

@Singleton
class SiftExpiryJobConfig @Inject()(config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.sift-expiry-job",
  jobName = "SiftExpiryJob"
)
