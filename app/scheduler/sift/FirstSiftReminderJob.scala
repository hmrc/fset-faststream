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

package scheduler.sift

import config.ScheduledJobConfig
import javax.inject.{Inject, Singleton}
import model.sift.{SiftFirstReminder, SiftReminderNotice}
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FirstSiftReminderJobImpl @Inject() (val service: ApplicationSiftService,
                                          val mongoComponent: ReactiveMongoComponent,
                                          val config: FirstSiftReminderJobConfig
                                         ) extends FirstSiftReminderJob

trait FirstSiftReminderJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: ApplicationSiftService
  val reminderNotice: SiftReminderNotice = SiftFirstReminder

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    service.nextApplicationForFirstReminder(reminderNotice.hoursBeforeReminder).flatMap {
      case None =>
        Logger.info("Sift first reminder job complete - NO applications found")
        Future.successful(())
      case Some(application) =>
        Logger.info(s"Sift first reminder job complete - one application found - ${application.applicationId}")
        service.sendReminderNotification(application, reminderNotice)
    }
  }
}

@Singleton
class FirstSiftReminderJobConfig @Inject() (config: Configuration) extends BasicJobConfig[ScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.sift-first-reminder-job",
  name = "SiftFirstReminderJob"
)
