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
import play.api.Logger
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.EvaluatePhase1ResultService

import scala.concurrent.{ ExecutionContext, Future }

object EvaluatePhase1ResultJob extends EvaluatePhase1ResultJob {
  val evaluateService: EvaluatePhase1ResultService = EvaluatePhase1ResultService
}

trait EvaluatePhase1ResultJob extends SingleInstanceScheduledJob with EvaluatePhase1ResultJobConfig {
  val evaluateService: EvaluatePhase1ResultService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    evaluateService.nextCandidateReadyForEvaluation.map {
      case Some((app, passmarkSettings)) =>
        Logger.debug(s"Phase1 evaluation for applicationId=${app.applicationId} against passmarkVersion=${passmarkSettings.version}")
        evaluateService.evaluate(app, passmarkSettings)
      case None =>
        Logger.info("Passmark settings or Application to evaluate phase1 result not found")
        Future.successful(())
    }
  }
}

trait EvaluatePhase1ResultJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  val conf = config.MicroserviceAppConfig.evaluatePhase1ResultJobConfig
  val configPrefix = "scheduling.online-testing.evaluate-phase1-result-job."
  val name = "EvaluatePhase1ResultJob"
}
