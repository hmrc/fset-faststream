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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model._
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object FailedSdipFsTestJob extends NotificationSdipFsTestJob {
  override val service = Phase1TestService
  override val notificationType = FailedSdipFsTestType

  val config = FailedPhase1TestJobConfig
}

trait NotificationSdipFsTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  val service: OnlineTestService
  val notificationType: NotificationTestTypeSdipFs

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = HeaderCarrier()
    service.processNextTestForSdipFsNotification(notificationType)
  }
}

object FailedSdipFsTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.failed-sdip-fs-test-job",
  name = "FailedSdipFsTestJob"
)
