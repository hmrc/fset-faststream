/*
 * Copyright 2020 HM Revenue & Customs
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
import model.AssessorNewEventsJobInfo
import org.joda.time.{ DateTime, Duration }
import scheduler.clustering.SingleInstanceScheduledJob
import services.AssessorsEventsSummaryJobsService
import services.assessor.AssessorService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object NotifyAssessorsOfNewEventsJob extends NotifyAssessorsOfNewEventsJob {
  val assessorService: AssessorService = AssessorService
  val config = NotifyAssessorsOfNewEventsJobConfig
  val assessorsEventsSummaryJobsService = AssessorsEventsSummaryJobsService
}

trait NotifyAssessorsOfNewEventsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  def assessorService: AssessorService
  def assessorsEventsSummaryJobsService: AssessorsEventsSummaryJobsService
  val TimeSpan = 24L

  def shouldRun(lastRun: DateTime, now: DateTime, isFirstJob: Boolean): Boolean = {
    if(isFirstJob) {
      true
    } else {
      val duration = new Duration(lastRun, now)
      duration.getStandardHours >= TimeSpan
    }
  }

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    assessorsEventsSummaryJobsService.lastRun.flatMap { lastRunInfoOpt =>
      val newLastRun = AssessorNewEventsJobInfo(DateTime.now)
      val lastRunInfo = lastRunInfoOpt.getOrElse(newLastRun)
      val isFirstJob = lastRunInfoOpt.isEmpty
      val canRun = shouldRun(lastRunInfo.lastRun, newLastRun.lastRun, isFirstJob)

      if (canRun) {
        assessorService.notifyAssessorsOfNewEvents(lastRunInfo.lastRun).flatMap { _ =>
          assessorsEventsSummaryJobsService.save(newLastRun).map(_ => ())
        }
      } else {
        Future.successful(())
      }
    }
  }
}

object NotifyAssessorsOfNewEventsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.notify-assessors-of-new-events-job",
  name = "NotifyAssessorsOfNewEventsJob"
)
