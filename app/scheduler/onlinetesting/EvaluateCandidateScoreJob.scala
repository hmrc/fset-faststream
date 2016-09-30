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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestPassmarkService
import model.ApplicationStatus._

import scala.concurrent.{ExecutionContext, Future}

object EvaluateCandidateScoreJob extends EvaluateCandidateScoreJob {
  val passmarkService: OnlineTestPassmarkService = OnlineTestPassmarkService
}

trait EvaluateCandidateScoreJob extends SingleInstanceScheduledJob with EvaluateCandidateScoreJobConfig {
  val passmarkService: OnlineTestPassmarkService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    passmarkService.nextCandidateScoreReadyForEvaluation.flatMap { applicationScoreOpt =>
      applicationScoreOpt.map { applicationScore => withName(applicationScore.applicationStatus) match {
        case ASSESSMENT_SCORES_ACCEPTED =>
          passmarkService.evaluateCandidateScoreWithoutChangingApplicationStatus(applicationScore)
        case _ =>
          passmarkService.evaluateCandidateScore(applicationScore)
      }}.getOrElse(Future.successful(()))
    }
  }
}

trait EvaluateCandidateScoreJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  val conf = config.MicroserviceAppConfig.evaluateCandidateScoreJobConfig
  val configPrefix = "scheduling.online-testing.evaluate-candidate-score-job."
  val name = "EvaluateCandidateScoreJob"
}
