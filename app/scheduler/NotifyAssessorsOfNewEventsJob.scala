/*
 * Copyright 2017 HM Revenue & Customs
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

package scheduler

import config.WaitingScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.assessoravailability.AssessorService
import services.personaldetails.PersonalDetailsService

import scala.concurrent.{ ExecutionContext, Future }

object NotifyAssessorsOfNewEventsJob extends NotifyAssessorsOfNewEventsJob {
  val assessorService: AssessorService = AssessorService
  val personalDetailsService: PersonalDetailsService = PersonalDetailsService
  val config = NotifyAssessorsOfNewEventsJobConfig
}

trait NotifyAssessorsOfNewEventsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val assessorService: AssessorService
  val personalDetailsService: PersonalDetailsService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    play.api.Logger.error("\n\n\n\n MY JOB WORKS\n\n\n\n\n")
    Future.successful(())
  }
}

object NotifyAssessorsOfNewEventsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.notify-assessors-of-new-events-job",
  name = "NotifyAssessorsOfNewEventsJob"
)
