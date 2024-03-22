/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.AssessorNewEventsJobInfo
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import scheduler.clustering.SingleInstanceScheduledJob
import services.AssessorsEventsSummaryJobsService
import services.assessor.AssessorService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Duration, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotifyAssessorsOfNewEventsJobImpl @Inject() (val assessorService: AssessorService,
                                                   val assessorsEventsSummaryJobsService: AssessorsEventsSummaryJobsService,
                                                   val mongoComponent: MongoComponent,
                                                   val config: NotifyAssessorsOfNewEventsJobConfig
                                                  ) extends NotifyAssessorsOfNewEventsJob {
}

trait NotifyAssessorsOfNewEventsJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  def assessorService: AssessorService
  def assessorsEventsSummaryJobsService: AssessorsEventsSummaryJobsService
  val TimeSpan = 24L

  def shouldRun(lastRun: OffsetDateTime, now: OffsetDateTime, isFirstJob: Boolean): Boolean = {
    if(isFirstJob) {
      true
    } else {
      val duration = Duration.between(lastRun, now)
      duration.toHours >= TimeSpan
    }
  }

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    assessorsEventsSummaryJobsService.lastRun.flatMap { lastRunInfoOpt =>
      val newLastRun = AssessorNewEventsJobInfo(OffsetDateTime.now)
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

@Singleton
class NotifyAssessorsOfNewEventsJobConfig @Inject() (config: Configuration) extends BasicJobConfig[WaitingScheduledJobConfig](
  config = config,
  configPrefix = "scheduling.notify-assessors-of-new-events-job",
  name = "NotifyAssessorsOfNewEventsJob"
)
