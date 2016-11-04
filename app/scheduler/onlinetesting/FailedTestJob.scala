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
import model._
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.{ OnlineTestService, Phase1TestService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object FailedPhase1TestJob extends FailedTestJob with FailedPhase1TestJobConfig {
  override val service = Phase1TestService
  override val failedType: FailedTestType = Phase1FailedTestType
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
}

trait FailedTestJob extends SingleInstanceScheduledJob {
  val service: OnlineTestService
  val failedType: FailedTestType

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = new HeaderCarrier()
    service.processNextFailedTestForNotification(failedType)
  }
}

trait FailedPhase1TestJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.failedPhase1TestJobConfig
  val configPrefix = "scheduling.online-testing.failed-phase1-test-job."
  val name = "FailedPhase1TestJob"
}
