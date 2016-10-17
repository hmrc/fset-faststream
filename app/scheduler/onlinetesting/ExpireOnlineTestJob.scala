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

import config.ScheduledJobConfig
import connectors.CSREmailClient
import model.{ ExpiryTest, Phase1ExpiryTest, Phase2ExpiryTest }
import repositories._
import scheduler.clustering.SingleInstanceScheduledJob
import services.AuditService
import services.onlinetesting._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ExpirePhase1TestJob extends ExpireOnlineTestJob with ExpirePhase1TestJobConfig {
  override val onlineTestingService = Phase1TestService
  override val expiryTest = Phase1ExpiryTest
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
}

object ExpirePhase2TestJob extends ExpireOnlineTestJob with ExpirePhase2TestJobConfig {
  override val onlineTestingService = Phase2TestService
  override val expiryTest = Phase2ExpiryTest
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
}

trait ExpireOnlineTestJob extends SingleInstanceScheduledJob {
  val onlineTestingService: OnlineTestService
  val expiryTest: ExpiryTest

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] =
    onlineTestingService.processNextExpiredTest(expiryTest)
}

trait ExpirePhase1TestJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.expirePhase1TestJobConfig
  val configPrefix = "scheduling.online-testing.expiry-phase1-job."
  val name = "ExpirePhase1TestJob"
}

trait ExpirePhase2TestJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.expirePhase2TestJobConfig
  val configPrefix = "scheduling.online-testing.expiry-phase2-job."
  val name = "ExpirePhase2TestJob"
}
