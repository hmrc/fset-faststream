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
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SecondPhase1ReminderExpiringTestJob @Inject() (val service: Phase1TestService,
                                                     val mongoComponent: ReactiveMongoComponent,
                                                     val config: SecondPhase1ReminderExpiringTestJobConfig
                                                    ) extends SecondReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase1SecondReminder
}

@Singleton
class SecondPhase2ReminderExpiringTestJob @Inject() (val service: Phase2TestService,
                                                     val mongoComponent: ReactiveMongoComponent,
                                                     val config: SecondPhase2ReminderExpiringTestJobConfig
                                                    )  extends SecondReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase2SecondReminder
}

@Singleton
class SecondPhase3ReminderExpiringTestJob @Inject() (val service: Phase3TestService,
                                                     val mongoComponent: ReactiveMongoComponent,
                                                     val config: SecondPhase3ReminderExpiringTestJobConfig
                                                    ) extends SecondReminderExpiringTestJob {
  override val reminderNotice: ReminderNotice = Phase3SecondReminder
}

trait SecondReminderExpiringTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val reminderNotice: ReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh: RequestHeader = EmptyRequestHeader
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}

@Singleton
class SecondPhase1ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.second-phase1-reminder-expiring-test-job",
  name = "SecondPhase1ReminderExpiringTestJob"
)

@Singleton
class SecondPhase2ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.second-phase2-reminder-expiring-test-job",
  name = "SecondPhase2ReminderExpiringTestJob"
)

@Singleton
class SecondPhase3ReminderExpiringTestJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.online-testing.second-phase3-reminder-expiring-test-job",
  name = "SecondPhase3ReminderExpiringTestJob"
)
