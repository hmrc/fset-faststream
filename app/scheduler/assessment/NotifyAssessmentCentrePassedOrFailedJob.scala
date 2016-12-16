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

package scheduler.assessment

import config.ScheduledJobConfig
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.applicationassessment.ApplicationAssessmentService

import scala.concurrent.{ ExecutionContext, Future }

object NotifyAssessmentCentrePassedOrFailedJob extends NotifyAssessmentCentrePassedOrFailedJob {
  val applicationAssessmentService: ApplicationAssessmentService = ApplicationAssessmentService
  val config = NotifyAssessmentCentrePassedOrFailedJobConfig
}

trait NotifyAssessmentCentrePassedOrFailedJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val applicationAssessmentService: ApplicationAssessmentService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] =
    applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication
}

object NotifyAssessmentCentrePassedOrFailedJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.notify-assessment-centre-passed-or-failed-job",
  name = "NotifyAssessmentCentrePassedOrFailedJob"
)
