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

package services.onlinetesting.phase3

import _root_.services.passmarksettings.PassMarkSettingsService
import config.LaunchpadGatewayConfig
import config.MicroserviceAppConfig.launchpadGatewayConfig
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest._
import model.Phase
import model.exchange.passmarksettings.Phase3PassMarkSettings
import model.persisted.ApplicationReadyForEvaluation
import play.api.Logger
import repositories._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.onlinetesting.ApplicationStatusCalculator

import scala.concurrent.Future

object EvaluatePhase3ResultService extends EvaluatePhase3ResultService {
  val evaluationRepository: OnlineTestEvaluationRepository
    = repositories.faststreamPhase3EvaluationRepository
  val passMarkSettingsRepo = phase3PassMarkSettingsRepository
  val launchpadGWConfig = launchpadGatewayConfig
  val phase = Phase.PHASE3
}

trait EvaluatePhase3ResultService extends EvaluateOnlineTestResultService[Phase3PassMarkSettings] with Phase3TestEvaluation
  with PassMarkSettingsService[Phase3PassMarkSettings] with ApplicationStatusCalculator {

  val launchpadGWConfig: LaunchpadGatewayConfig

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase3PassMarkSettings): Future[Unit] = {
    Logger.debug(s"Evaluating Phase3 appId=${application.applicationId}")

    val optLaunchpadTest = application.activeLaunchpadTest
    require(optLaunchpadTest.isDefined, "Active launchpad test not found")
    require(application.prevPhaseEvaluation.isDefined, "Phase2 results required to evaluate Phase3")

    val optLatestReviewed = optLaunchpadTest.map(_.callbacks.reviewed).flatMap(getLatestReviewed)
    if (launchpadGWConfig.phase3Tests.verifyAllScoresArePresent) {
      require(optLatestReviewed.exists(_.allQuestionsReviewed),
        s"Some of the launchpad questions are not reviewed for application Id = ${application.applicationId}")
    }

    val schemeResults = (optLatestReviewed, application.prevPhaseEvaluation) match {
      case (Some(launchpadReview), Some(prevPhaseEvaluation)) =>
        evaluate(application.applicationRoute, application.preferences.schemes, launchpadReview, prevPhaseEvaluation.result, passmark)

      case _ => throw new IllegalStateException(s"Illegal number of phase3 active tests with results " +
        s"for this application: ${application.applicationId}")
    }
    savePassMarkEvaluation(application, schemeResults, passmark)
  }
}
