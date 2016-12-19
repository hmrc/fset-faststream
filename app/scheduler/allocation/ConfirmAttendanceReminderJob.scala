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

package scheduler.allocation

import config.ScheduledJobConfig
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.allocation.CandidateAllocationService

import scala.concurrent.{ ExecutionContext, Future }

object ConfirmAttendanceReminderJob extends ConfirmAttendanceReminderJob {
  val candidateAllocationService = CandidateAllocationService
  val config = ConfirmAttendanceReminderJobConfig
}

trait ConfirmAttendanceReminderJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val candidateAllocationService: CandidateAllocationService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    candidateAllocationService.nextUnconfirmedCandidateForSendingReminder.flatMap {
      case Some(candidate) =>
        candidateAllocationService.sendEmailConfirmationReminder(candidate)
      case None =>
        Future.successful(())
    }
  }
}

object ConfirmAttendanceReminderJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.confirm-attendance-reminder-job",
  name = "ConfirmAttendanceReminderJob"
)
