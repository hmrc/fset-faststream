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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model._
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting._
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object ExpirePhase1TestJob extends ExpireOnlineTestJob {
  override val onlineTestingService = Phase1TestService
  override val expiryTest = Phase1ExpirationEvent
  val config = ExpirePhase1TestJobConfig
}

object ExpirePhase2TestJob extends ExpireOnlineTestJob {
  override val onlineTestingService = Phase2TestService
  override val expiryTest = Phase2ExpirationEvent
  val config = ExpirePhase2TestJobConfig
}

object ExpirePhase3TestJob extends ExpireOnlineTestJob {
  override val onlineTestingService = Phase3TestService
  override val expiryTest = Phase3ExpirationEvent
  val config = ExpirePhase3TestJobConfig
}

trait ExpireOnlineTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val onlineTestingService: OnlineTestService
  val expiryTest: TestExpirationEvent

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    implicit val rh = EmptyRequestHeader
    Logger.debug(s"Running online test expiry job for ${expiryTest.phase}")
    onlineTestingService.processNextExpiredTest(expiryTest)
  }
}

object ExpirePhase1TestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.expiry-phase1-job",
  name = "ExpirePhase1TestJob"
)

object ExpirePhase2TestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.expiry-phase2-job",
  name = "ExpirePhase2TestJob"
)

object ExpirePhase3TestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.expiry-phase3-job",
  name = "ExpirePhase3TestJob"
)
