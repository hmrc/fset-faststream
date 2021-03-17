/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.{ Inject, Singleton }
import model._
import play.api.Configuration
import play.api.mvc.RequestHeader
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class FailedSdipFsTestJob @Inject() (val service: Phase1TestService,
                                     val mongoComponent: ReactiveMongoComponent,
                                     val config: FailedSdipFsTestJobConfig) extends NotificationSdipFsTestJob {
  override val notificationType = FailedSdipFsTestType
}

trait NotificationSdipFsTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {

  val service: OnlineTestService
  val notificationType: NotificationTestTypeSdipFs

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh:RequestHeader = EmptyRequestHeader
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForSdipFsNotification(notificationType)
  }
}

class FailedSdipFsTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.failed-sdip-fs-test-job",
  name = "FailedSdipFsTestJob"
)
