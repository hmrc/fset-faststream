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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model._
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase3.Phase3TestService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object SuccessPhase1TestJob extends SuccessTestJob {
  override val service = Phase1TestService
  override val successType: SuccessTestType = Phase1SuccessTestType
  val config = SuccessPhase1TestJobConfig
}

object SuccessPhase3TestJob extends SuccessTestJob {
  override val service = Phase3TestService
  override val successType: SuccessTestType = Phase3SuccessTestType
  val config = SuccessPhase3TestJobConfig
}

object SuccessPhase3SdipFsTestJob extends SuccessTestJob {
  override val service = Phase3TestService
  override val successType: SuccessTestType = Phase3SuccessSdipFsTestType
  val config = SuccessPhase3TestJobConfig
}

trait SuccessTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val successType: SuccessTestType

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = new HeaderCarrier()
    service.processNextTestForNotification(successType)
  }
}

object SuccessPhase1TestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.success-phase1-test-job",
  name = "SuccessPhase1TestJob"
)

object SuccessPhase3TestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.success-phase3-test-job",
  name = "SuccessPhase3TestJob"
)

object SuccessPhase3SdipFsTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.success-phase3-sdipfs-test-job",
  name = "SuccessPhase3SdipFsTestJob"
)
