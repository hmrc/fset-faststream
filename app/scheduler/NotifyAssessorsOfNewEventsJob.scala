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
import model.AssessorNewEventsJobInfo
import org.joda.time.{ DateTime, Duration }
import scheduler.clustering.SingleInstanceScheduledJob
import services.AssessorsEventsSummaryJobsService
import services.assessoravailability.AssessorService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object NotifyAssessorsOfNewEventsJob extends NotifyAssessorsOfNewEventsJob {
  val assessorService: AssessorService = AssessorService
  val config = NotifyAssessorsOfNewEventsJobConfig
  val assessorsEventsSummaryJobsService = AssessorsEventsSummaryJobsService
}

trait NotifyAssessorsOfNewEventsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  def assessorService: AssessorService
  def assessorsEventsSummaryJobsService: AssessorsEventsSummaryJobsService
  val TIMESPAN = 24

  def shouldRun(lastRun: DateTime, now: DateTime, isFirstJob: Boolean): Boolean = {
    if(isFirstJob) {
      true
    } else {
      val duration = new Duration(lastRun, now)
      duration.getStandardHours >= TIMESPAN
    }
  }

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    for {
      lastRunInfoOpt <- assessorsEventsSummaryJobsService.lastRun
      newLastRun = AssessorNewEventsJobInfo(DateTime.now)
      lastRunInfo = lastRunInfoOpt.getOrElse(newLastRun)
      isFirstJob = lastRunInfoOpt.isEmpty
      _ <- assessorService.notifyAssessorsOfNewEvents(lastRunInfo.lastRun) if shouldRun(lastRunInfo.lastRun, newLastRun.lastRun, isFirstJob)
      _ <- assessorsEventsSummaryJobsService.save(newLastRun)
    } yield {}
  }
}

object NotifyAssessorsOfNewEventsJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.notify-assessors-of-new-events-job",
  name = "NotifyAssessorsOfNewEventsJob"
)
