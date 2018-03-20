/*
 * Copyright 2018 HM Revenue & Customs
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
import model.sift.{ SiftReminderNotice, SiftSecondReminder }
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object SecondSiftReminderJob extends SecondSiftReminderJob {
  override val service = ApplicationSiftService
  override val reminderNotice: SiftReminderNotice = SiftSecondReminder
  val config = SecondSiftReminderJobConfig
}

trait SecondSiftReminderJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: ApplicationSiftService
  val reminderNotice: SiftReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    service.processNextApplicationForSecondReminder(reminderNotice.hoursBeforeReminder).flatMap {
      case None =>
        Logger.info("Sift second reminder job complete - NO applications found")
        Future.successful(())
      case Some(application) =>
        Logger.info(s"Sift second reminder job complete - one application found - ${application.applicationId}")
        service.sendReminderNotification(application, reminderNotice)
    }
  }
}

object SecondSiftReminderJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.sift-second-reminder-job",
  name = "SiftSecondReminderJob"
)
