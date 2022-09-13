/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model._
import play.api.Configuration
import play.api.mvc.RequestHeader
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class FirstPhase1ReminderExpiringTestJob @Inject() (val service: Phase1TestService,
                                                    val mongoComponent: MongoComponent,
                                                    val config: FirstPhase1ReminderExpiringTestJobConfig
                                                   ) extends FirstReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase1FirstReminder
}

@Singleton
class FirstPhase2ReminderExpiringTestJob @Inject() (val service: Phase2TestService,
                                                    val mongoComponent: MongoComponent,
                                                    val config: FirstPhase2ReminderExpiringTestJobConfig
                                                   ) extends FirstReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase2FirstReminder
}

@Singleton
class FirstPhase3ReminderExpiringTestJob @Inject() (val service: Phase3TestService,
                                                    val mongoComponent: MongoComponent,
                                                    val config: FirstPhase3ReminderExpiringTestJobConfig
                                                   ) extends FirstReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase3FirstReminder
}

trait FirstReminderExpiringTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val reminderNotice: ReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh: RequestHeader = EmptyRequestHeader
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}

@Singleton
class FirstPhase1ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.first-phase1-reminder-expiring-test-job",
  name = "FirstPhase1ReminderExpiringTestJob"
)

@Singleton
class FirstPhase2ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.first-phase2-reminder-expiring-test-job",
  name = "FirstPhase2ReminderExpiringTestJob"
)

@Singleton
class FirstPhase3ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.first-phase3-reminder-expiring-test-job",
  name = "FirstPhase3ReminderExpiringTestJob"
)
