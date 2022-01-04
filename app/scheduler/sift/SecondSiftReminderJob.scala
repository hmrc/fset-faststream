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

package scheduler.sift

import config.ScheduledJobConfig
import javax.inject.{ Inject, Singleton }
import model.sift.{ SiftReminderNotice, SiftSecondReminder }
import play.api.{ Configuration, Logging }
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SecondSiftReminderJobImpl @Inject() (val service: ApplicationSiftService,
                                           val mongoComponent: ReactiveMongoComponent,
                                           val config: SecondSiftReminderJobConfig) extends SecondSiftReminderJob {
  //  override val service = ApplicationSiftService
  override val reminderNotice: SiftReminderNotice = SiftSecondReminder
  //  val config = SecondSiftReminderJobConfig
}

trait SecondSiftReminderJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] with Logging {
  val service: ApplicationSiftService
  val reminderNotice: SiftReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    service.nextApplicationForSecondReminder(reminderNotice.hoursBeforeReminder).flatMap {
      case None =>
        logger.info("Sift second reminder job complete - No applications found")
        Future.successful(())
      case Some(application) =>
        logger.info(s"Sift second reminder job complete - one application found - ${application.applicationId}")
        service.sendReminderNotification(application, reminderNotice)
    }
  }
}

@Singleton
class SecondSiftReminderJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.sift-second-reminder-job",
  name = "SiftSecondReminderJob"
)
