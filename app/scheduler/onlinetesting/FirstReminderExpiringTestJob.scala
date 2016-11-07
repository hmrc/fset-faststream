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
import model.{ EmptyRequestHeader, Phase1FirstReminder, Phase2FirstReminder, ReminderNotice }
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.{ OnlineTestService, Phase1TestService, Phase2TestService }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object FirstPhase1ReminderExpiringTestJob extends FirstReminderExpiringTestJob with FirstPhase1ReminderExpiringTestJobConfig {
  override val service = Phase1TestService
  override val reminderNotice: ReminderNotice = Phase1FirstReminder
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
}

object FirstPhase2ReminderExpiringTestJob extends FirstReminderExpiringTestJob with FirstPhase2ReminderExpiringTestJobConfig {
  override val service = Phase2TestService
  override val reminderNotice: ReminderNotice = Phase2FirstReminder
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
}

trait FirstReminderExpiringTestJob extends SingleInstanceScheduledJob {
  val service: OnlineTestService
  val reminderNotice: ReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = new HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}

trait FirstPhase1ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.firstPhase1ReminderJobConfig
  val configPrefix = "scheduling.online-testing.first-phase1-reminder-expiring-test-job."
  val name = "FirstPhase1ReminderExpiringTestJob"
}

trait FirstPhase2ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.firstPhase2ReminderJobConfig
  val configPrefix = "scheduling.online-testing.first-phase2-reminder-expiring-test-job."
  val name = "FirstPhase2ReminderExpiringTestJob"
}
