/*
 * Copyright 2019 HM Revenue & Customs
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

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.BasicJobConfig
import ProgressToSiftJobConfig.conf
import scheduler.clustering.SingleInstanceScheduledJob
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object SiftExpiryJob extends SiftExpiryJob {
  val siftService = ApplicationSiftService
  val config = SiftExpiryJobConfig
}

trait SiftExpiryJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val siftService: ApplicationSiftService
  lazy val batchSize = conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    Logger.info("Expiring candidates in SIFT")
    siftService.processExpiredCandidates(batchSize).map(_ => ())
  }
}

object SiftExpiryJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.sift-expiry-job",
  name = "SiftExpiryJob"
)