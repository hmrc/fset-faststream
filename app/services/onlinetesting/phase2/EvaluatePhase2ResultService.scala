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

package services.onlinetesting.phase2

import _root_.services.passmarksettings.PassMarkSettingsService
import model.Phase
import model.exchange.passmarksettings.Phase2PassMarkSettings
import model.persisted.ApplicationReadyForEvaluation
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService
import services.onlinetesting.CurrentSchemeStatusHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluatePhase2ResultService extends EvaluatePhase2ResultService {
  val evaluationRepository = repositories.faststreamPhase2EvaluationRepository
  val passMarkSettingsRepo = phase2PassMarkSettingsRepository
  val generalAppRepository = repositories.applicationRepository
  val phase = Phase.PHASE2
}

trait EvaluatePhase2ResultService extends EvaluateOnlineTestResultService[Phase2PassMarkSettings] with Phase2TestEvaluation
  with PassMarkSettingsService[Phase2PassMarkSettings] with CurrentSchemeStatusHelper {

  def evaluate(implicit application: ApplicationReadyForEvaluation, passmark: Phase2PassMarkSettings): Future[Unit] = {
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

    getSdipResults(application).flatMap { sdip =>
      if (application.isSdipFaststream) {
        Logger.debug(s"Phase2 appId=${application.applicationId} Sdip faststream application will persist the following Sdip results " +
          s"read from current scheme status: $sdip")
      }
      savePassMarkEvaluation(application, schemeResults ++ sdip , passmark)
    }
  }
}
