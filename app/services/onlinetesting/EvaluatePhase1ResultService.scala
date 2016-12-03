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

import _root_.services.passmarksettings.PassMarkSettingsService
import services.onlinetesting.phase1.{ Phase1TestEvaluation, Phase1TestSelector }
import config.MicroserviceAppConfig._
import model.Phase
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.ApplicationReadyForEvaluation
import play.api.Logger
import repositories._
import scheduler.onlinetesting.EvaluateOnlineTestResultService

import scala.concurrent.Future

object EvaluatePhase1ResultService extends EvaluatePhase1ResultService {
  val evaluationRepository = repositories.faststreamPhase1EvaluationRepository
  val passMarkSettingsRepo = phase1PassMarkSettingsRepository
  val gatewayConfig = cubiksGatewayConfig
  val phase = Phase.PHASE1
}

trait EvaluatePhase1ResultService extends EvaluateOnlineTestResultService[Phase1PassMarkSettings] with Phase1TestSelector with
  Phase1TestEvaluation with PassMarkSettingsService[Phase1PassMarkSettings] {

  def evaluate(application: ApplicationReadyForEvaluation, passmark: Phase1PassMarkSettings): Future[Unit] = {
    Logger.debug(s"Evaluating phase1 appId=${application.applicationId}")

    val activeTests = application.activeCubiksTests
    require(activeTests.nonEmpty && activeTests.length <= 2, "Allowed active number of tests is 1 or 2")
    val sjqTestOpt = findFirstSjqTest(activeTests)
    val bqTestOpt = findFirstBqTest(activeTests)

    val schemeResults = (sjqTestOpt, bqTestOpt) match {
      case (Some(sjqTest), None) if application.isGis && sjqTest.testResult.isDefined =>
        evaluateForGis(application.preferences.schemes, sjqTest.testResult.get, passmark)
      case (Some(sjqTest), Some(bqTest)) if application.nonGis && sjqTest.testResult.isDefined && bqTest.testResult.isDefined =>
        evaluateForNonGis(application.preferences.schemes, sjqTest.testResult.get, bqTest.testResult.get, passmark)
      case _ =>
        throw new IllegalStateException(s"Illegal number of active tests with results for this application: ${application.applicationId}")
    }
    savePassMarkEvaluation(application, schemeResults, passmark)
  }
}
