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

package services.onlinetesting

import _root_.services.onlinetesting.phase2.Phase2TestEvaluation
import _root_.services.passmarksettings.PassMarkSettingsService
import config.MicroserviceAppConfig._
import model.Phase
import model.exchange.passmarksettings.Phase2PassMarkSettings
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation }
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService

import scala.concurrent.Future

object EvaluatePhase2ResultService extends EvaluatePhase2ResultService {
  val evaluationRepository = repositories.faststreamPhase2EvaluationRepository
  val gatewayConfig = cubiksGatewayConfig
  val passMarkSettingsRepo = phase2PassMarkSettingsRepository
}

trait EvaluatePhase2ResultService extends EvaluateOnlineTestResultService[Phase2PassMarkSettings] with Phase2TestEvaluation
  with PassMarkSettingsService[Phase2PassMarkSettings] with ApplicationStatusCalculator {

  def evaluate(application: ApplicationReadyForEvaluation, passmark: Phase2PassMarkSettings): Future[Unit] = {
    Logger.debug(s"Evaluating phase2 appId=${application.applicationId}")

    val activeTests = application.activeCubiksTests
    require(activeTests.nonEmpty && activeTests.length == 1, "Allowed active number of tests is 1")
    require(application.prevPhaseEvaluation.isDefined, "Phase1 results required to evaluate phase2")

    val optEtrayResult = activeTests.headOption.flatMap(_.testResult)

    val schemeResults = (optEtrayResult, application.prevPhaseEvaluation) match {
      case (Some(etrayTest), Some(prevPhaseEvaluation)) =>
        evaluate(application.preferences.schemes, etrayTest, prevPhaseEvaluation.result, passmark)
      case _ => throw new IllegalStateException(s"Illegal number of phase2 active tests with results " +
        s"for this application: ${application.applicationId}")
    }

    schemeResults.nonEmpty match {
      case true => evaluationRepository.savePassmarkEvaluation(
        application.applicationId,
        PassmarkEvaluation(passmark.version, application.prevPhaseEvaluation.map(_.passmarkVersion), schemeResults),
        determineApplicationStatus(application.applicationStatus, schemeResults, Phase.PHASE2)
      )
      case false => Future.successful(())
    }
  }
}



